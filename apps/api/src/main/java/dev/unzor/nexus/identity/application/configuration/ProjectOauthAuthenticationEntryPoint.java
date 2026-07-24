package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.identity.application.service.ProjectAllowedOriginsResolver;
import dev.unzor.nexus.identity.application.service.WebOrigin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Entry point de la cadena del Authorization Server para peticiones HTML no
 * autenticadas bajo un proyecto: redirige al <b>login embebido de la app</b> de ese
 * proyecto en vez de al login global.
 *
 * <p>El destino se deriva <b>por cliente</b> del propio authorize request: el origen
 * del {@code redirect_uri} que el cliente envió, siempre que sea un origen registrado
 * del proyecto (vía {@link ProjectAllowedOriginsResolver}). Así cada app recibe al
 * usuario en su propio dominio ({@code <app-origin>/p/{slug}/login?continue=...}) sin
 * que el operador configure nada. Si el {@code redirect_uri} falta o no es un origen
 * registrado, se cae al {@code frontend-base-url} (página hosteada por Nexus). Para
 * rutas globales (sin proyecto) cae a {@code /oauth2/authentication-required}.</p>
 *
 * <p>El {@code continue} es la URL <b>absoluta del API</b> del endpoint de autorización
 * ({@code /p/{slug}/oauth2/authorize?...}): tras el login JSON el frontend de la app
 * hace una navegación de alto nivel a esa URL, con lo que la cookie de sesión viaja al
 * host del API y el Authorization Server reanuda el flujo leyendo el SecurityContext de
 * la sesión.</p>
 */
@Component
public class ProjectOauthAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final String frontendBaseUrl;
    private final ProjectAllowedOriginsResolver allowedOriginsResolver;

    public ProjectOauthAuthenticationEntryPoint(
            @Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
            ProjectAllowedOriginsResolver allowedOriginsResolver
    ) {
        this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/+$", "");
        this.allowedOriginsResolver = allowedOriginsResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        String slug = extractSlug(request.getRequestURI());
        if (slug == null) {
            response.sendRedirect(frontendBaseUrl + "/oauth2/authentication-required");
            return;
        }
        // continue = URL absoluta del API que el AS debe reanudar tras el login.
        String absoluteAuthorizeUrl = absoluteApiUrl(request);
        String base = resolveLoginOrigin(request, slug).orElse(frontendBaseUrl);
        String target = base + "/p/" + slug + "/login?continue="
                + URLEncoder.encode(absoluteAuthorizeUrl, StandardCharsets.UTF_8);
        response.sendRedirect(target);
    }

    /**
     * Origen del login embebido: el del {@code redirect_uri} del authorize request si
     * está registrado como origen permitido del proyecto. Vacío en caso contrario
     * (open-redirect safe: nunca se confía en el parámetro crudo sin validar).
     */
    private java.util.Optional<String> resolveLoginOrigin(HttpServletRequest request, String slug) {
        String redirectUri = request.getParameter("redirect_uri");
        String candidate = WebOrigin.of(redirectUri);
        if (candidate == null) {
            return java.util.Optional.empty();
        }
        Set<String> allowed = allowedOriginsResolver.originsForSlug(slug);
        return allowed.contains(candidate)
                ? java.util.Optional.of(candidate)
                : java.util.Optional.empty();
    }

    private static String absoluteApiUrl(HttpServletRequest request) {
        StringBuilder b = new StringBuilder()
                .append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        if (port > 0 && !isDefaultPort(request.getScheme(), port)) {
            b.append(':').append(port);
        }
        b.append(request.getRequestURI());
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            b.append('?').append(query);
        }
        return b.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    }

    /** Extrae el slug de una ruta {@code /p/{slug}/...}. */
    private static String extractSlug(String requestUri) {
        if (requestUri == null || !requestUri.startsWith("/p/")) {
            return null;
        }
        String rest = requestUri.substring(3);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }
}
