package dev.unzor.nexus.identity.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Construye los enlaces públicos que se envían por email a los usuarios finales de un
 * proyecto (verificación de email, reseteo de contraseña).
 *
 * <p>Estos enlaces apuntan al <b>frontend Next.js</b> (donde viven las páginas de usuario
 * final), no al API: el usuario aterriza en la página Next.js, que a su vez llama a la
 * API JSON correspondiente. El base URL se toma de {@code nexus.frontend-base-url}
 * (siempre configurado; por defecto {@code http://localhost:3000} en desarrollo).</p>
 */
@Component
public class EndUserLinkBuilder {

    private final String frontendBaseUrl;

    public EndUserLinkBuilder(@Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
    }

    public String verifyEmailLink(String projectSlug, String rawToken) {
        return base() + "/p/" + projectSlug + "/verify-email?token=" + urlEncode(rawToken);
    }

    public String passwordResetLink(String projectSlug, String rawToken) {
        return base() + "/p/" + projectSlug + "/password-reset/confirm?token=" + urlEncode(rawToken);
    }

    private String base() {
        return frontendBaseUrl.replaceAll("/+$", "");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
