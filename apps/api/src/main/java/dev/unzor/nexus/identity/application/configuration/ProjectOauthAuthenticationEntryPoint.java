package dev.unzor.nexus.identity.application.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Entry point de la cadena del Authorization Server para peticiones HTML no
 * autenticadas bajo un proyecto: redirige al login de ese proyecto
 * ({@code /p/{slug}/login?continue=...}) en vez de al login global. Para rutas
 * que no pertenezcan a un proyecto, cae al {@code /oauth2/authentication-required}.
 */
@Component
public class ProjectOauthAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        String slug = extractSlug(request.getRequestURI());
        if (slug == null) {
            response.sendRedirect("/oauth2/authentication-required");
            return;
        }
        String original = request.getRequestURI()
                + (request.getQueryString() == null || request.getQueryString().isBlank() ? "" : "?" + request.getQueryString());
        String target = "/p/" + slug + "/login?continue=" + URLEncoder.encode(original, StandardCharsets.UTF_8);
        response.sendRedirect(target);
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
