package dev.unzor.nexus.projects.api.requests;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Valida una URL absoluta con esquema {@code http} o {@code https} y host no
 * vacío. Acepta {@code null} (campo opcional).
 */
public class HttpsUrlValidator implements ConstraintValidator<HttpsUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            URL url = new URL(value);
            String protocol = url.getProtocol();
            String host = url.getHost();
            return ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))
                    && host != null
                    && !host.isBlank();
        } catch (MalformedURLException exception) {
            return false;
        }
    }
}
