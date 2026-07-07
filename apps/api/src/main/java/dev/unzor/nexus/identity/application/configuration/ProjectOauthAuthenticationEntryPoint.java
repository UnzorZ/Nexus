package dev.unzor.nexus.identity.application.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Entry point de la cadena del Authorization Server para peticiones HTML no
 * autenticadas bajo un proyecto: redirige al <b>login Next.js</b> de ese proyecto
 * ({@code {frontend-base}/p/{slug}/login?continue=...}) en vez de al login global.
 *
 * <p>El {@code continue} es la URL <b>absoluta del API</b> del endpoint de autorización
 * ({@code /p/{slug}/oauth2/authorize?...}): tras el login JSON el frontend Next.js hace
 * una navegación de alto nivel a esa URL, con lo que la cookie de sesión viaja al host
 * del API y el Authorization Server reanuda el flujo leyendo el SecurityContext de la
 * sesión. Para rutas globales (sin proyecto) cae a la página Next.js
 * {@code /oauth2/authentication-required}.</p>
 */
@Component
public class ProjectOauthAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final String frontendBaseUrl;

    public ProjectOauthAuthenticationEntryPoint(
            @Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/+$", "");
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
        String target = frontendBaseUrl + "/p/" + slug + "/login?continue="
                + URLEncoder.encode(absoluteAuthorizeUrl, StandardCharsets.UTF_8);
        response.sendRedirect(target);
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
