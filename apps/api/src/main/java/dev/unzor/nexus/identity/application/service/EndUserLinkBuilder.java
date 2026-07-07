package dev.unzor.nexus.identity.application.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Construye los enlaces públicos que se envían por email a los usuarios finales de un
 * proyecto (verificación de email, reseteo de contraseña).
 *
 * <p>El base URL se toma de {@code nexus.api.external-base-url} si está configurado;
 * si no, se deriva del origin del request entrante (mismo origen que el issuer OAuth
 * por-request). Esto evita confiar ciegamente en cabeceras {@code X-Forwarded-*}
 * (spoofable) salvo que el operador lo configure explícitamente tras un proxy de
 * confianza.</p>
 */
@Component
public class EndUserLinkBuilder {

    private final String externalBaseUrl;

    public EndUserLinkBuilder(@Value("${nexus.api.external-base-url:}") String externalBaseUrl) {
        this.externalBaseUrl = externalBaseUrl == null ? "" : externalBaseUrl.trim();
    }

    public String verifyEmailLink(String projectSlug, String rawToken) {
        return base() + "/p/" + projectSlug + "/verify-email?token=" + urlEncode(rawToken);
    }

    public String passwordResetLink(String projectSlug, String rawToken) {
        return base() + "/p/" + projectSlug + "/password-reset/confirm?token=" + urlEncode(rawToken);
    }

    private String base() {
        if (!externalBaseUrl.isEmpty()) {
            return externalBaseUrl.replaceAll("/+$", "");
        }
        HttpServletRequest request = currentRequest();
        return (request != null) ? originOf(request) : "http://localhost:8080";
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes sra) ? sra.getRequest() : null;
    }

    private static String originOf(HttpServletRequest request) {
        StringBuilder b = new StringBuilder()
                .append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        if (port > 0 && !isDefaultPort(request.getScheme(), port)) {
            b.append(':').append(port);
        }
        return b.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
