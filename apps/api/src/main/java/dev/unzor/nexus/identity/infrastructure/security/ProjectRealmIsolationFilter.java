package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro de aislamiento entre realms OAuth por proyecto (remediación de auditoría,
 * hallazgo crítico). Para toda petición autenticada bajo {@code /api/p/{slug}/**} o
 * {@code /p/{slug}/**} exige que el proyecto del realm solicitado coincida con el del
 * {@link ProjectUserPrincipal} establecido en sesión.
 *
 * <p>Sin este gate, una sesión abierta en el realm A podía reutilizarse contra el
 * realm B: el token emitido llevaba {@code iss} y {@code project_id} de B pero
 * permisos del usuario de A (fuga cross-realm en {@code ProjectIdTokenCustomizer}),
 * y los endpoints JSON ({@code /me}, MFA, sesiones) operaban sobre
 * {@code principal.projectId()} ignorando el slug del path.</p>
 *
 * <p>Sólo actúa cuando hay un {@link ProjectUserPrincipal} autenticado: las rutas
 * {@code permitAll} (login, registro, reset…) viajan anónimas y pasan sin tocar el
 * gate; si el slug no resuelve a un proyecto no se rechaza aquí (el handler devolverá
 * su 404 habitual). Ante desajuste responde 401, coherente con las entry points de
 * ambas cadenas ({@code /p/**} y {@code /api/p/**}). No destruye la sesión: sigue
 * siendo válida para su realm legítimo.</p>
 */
public class ProjectRealmIsolationFilter extends OncePerRequestFilter {

    private final ProjectSlugResolver slugResolver;

    public ProjectRealmIsolationFilter(ProjectSlugResolver slugResolver) {
        this.slugResolver = slugResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String slug = extractSlug(request.getRequestURI());
        if (slug != null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof ProjectUserPrincipal pup) {
                UUID realmProjectId = resolveQuietly(slug);
                if (realmProjectId != null && !realmProjectId.equals(pup.projectId())) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private UUID resolveQuietly(String slug) {
        try {
            return slugResolver.resolve(slug).projectId();
        } catch (RuntimeException unknown) {
            return null;
        }
    }

    /** Extrae el {slug} de {@code /api/p/{slug}/**} o {@code /p/{slug}/**}; null fuera de ambos. */
    static String extractSlug(String requestUri) {
        String prefix;
        if (requestUri.startsWith("/api/p/")) {
            prefix = "/api/p/";
        } else if (requestUri.startsWith("/p/")) {
            prefix = "/p/";
        } else {
            return null;
        }
        String rest = requestUri.substring(prefix.length());
        int slash = rest.indexOf('/');
        String slug = slash < 0 ? rest : rest.substring(0, slash);
        return slug.isEmpty() ? null : slug;
    }
}
