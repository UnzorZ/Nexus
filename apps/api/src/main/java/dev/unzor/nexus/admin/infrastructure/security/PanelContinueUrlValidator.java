package dev.unzor.nexus.admin.infrastructure.security;

import java.net.URI;

/**
 * Valida que {@code continue} apunte al frontend configurado y evite open redirects.
 */
public final class PanelContinueUrlValidator {

    private final String frontendBaseUrl;

    public PanelContinueUrlValidator(String frontendBaseUrl) {
        this.frontendBaseUrl = normalizeBaseUrl(frontendBaseUrl);
    }

    public boolean isAllowed(String continueUrl) {
        if (continueUrl == null || continueUrl.isBlank()) {
            return false;
        }
        try {
            URI continueUri = URI.create(continueUrl);
            URI frontendUri = URI.create(frontendBaseUrl);
            return frontendUri.getScheme().equalsIgnoreCase(continueUri.getScheme())
                    && frontendUri.getHost().equalsIgnoreCase(continueUri.getHost())
                    && frontendUri.getPort() == continueUri.getPort();
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String defaultDashboardUrl() {
        return frontendBaseUrl + "/dashboard";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
