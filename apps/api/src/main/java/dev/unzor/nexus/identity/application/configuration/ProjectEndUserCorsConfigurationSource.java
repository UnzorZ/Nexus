package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.identity.application.service.ProjectAllowedOriginsResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fuente CORS dinámica para la API JSON de usuario final ({@code /api/p/**}). Los
 * orígenes permitidos se derivan, por petición, de los clientes OAuth ACTIVOS del
 * proyecto del realm (sus redirect URIs) —vía {@link ProjectAllowedOriginsResolver}—
 * más una base de instancia ({@code nexus.frontend-base-url} +
 * {@code nexus.allowed-dev-origins}). Así un proyecto autoriza su propio frontend al
 * registrar su redirect URI, sin que el operador edite la configuración de Nexus.
 *
 * <p><b>No es un bean.</b> Se instancia y se inyecta sólo en la cadena
 * {@code /api/p/**} vía {@code .cors(c -> c.configurationSource(...))}. Si se
 * registrase como bean, Spring Security lo aplicaría también a {@code /api/panel/**}
 * —que usa {@code .cors(withDefaults())} y debe conservar su fuente MVC estática—
 * porque {@code CorsConfigurer} consume cualquier bean {@link CorsConfigurationSource}
 * para todas las cadenas con {@code withDefaults()}.</p>
 *
 * <p>Orígenes exactos con credenciales: nunca el comodín {@code *}. Un origen no
 * registrado provoca el 403 "Invalid CORS request" de {@code DefaultCorsProcessor}
 * (coincide con el comportamiento observado hoy). Las peticiones sin cabecera
 * {@code Origin} no son CORS del navegador: devuelven {@code null} (sin cabeceras)
 * y pasan por la cadena normalmente.</p>
 */
final class ProjectEndUserCorsConfigurationSource implements CorsConfigurationSource {

    private static final long PREFLIGHT_MAX_AGE_SECONDS = 1_800L;

    private final ProjectAllowedOriginsResolver resolver;
    private final Set<String> baselineOrigins;

    ProjectEndUserCorsConfigurationSource(
            ProjectAllowedOriginsResolver resolver,
            Set<String> baselineOrigins
    ) {
        this.resolver = resolver;
        this.baselineOrigins = baselineOrigins;
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return null;
        }
        Set<String> allowed = new LinkedHashSet<>(baselineOrigins);
        allowed.addAll(resolver.originsForSlug(extractSlug(request.getRequestURI())));

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(new ArrayList<>(allowed));
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);
        return config;
    }

    /** Extrae el {slug} de {@code /api/p/{slug}/**}; null fuera de ese prefijo. */
    private static String extractSlug(String requestUri) {
        if (requestUri == null || !requestUri.startsWith("/api/p/")) {
            return null;
        }
        String rest = requestUri.substring("/api/p/".length());
        int slash = rest.indexOf('/');
        String slug = slash < 0 ? rest : rest.substring(0, slash);
        return slug.isEmpty() ? null : slug;
    }
}
