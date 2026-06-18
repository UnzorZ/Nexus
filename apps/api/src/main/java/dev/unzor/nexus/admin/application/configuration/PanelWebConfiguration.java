package dev.unzor.nexus.admin.application.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
class PanelWebConfiguration {

    /**
     * Orígenes permitidos para las llamadas cross-origin del panel. Además de
     * {@code nexus.frontend-base-url} se aceptan los orígenes adicionales de
     * {@code nexus.allowed-dev-origins} (lista separada por comas), para facilitar
     * el desarrollo desde varios orígenes (p. ej. distintos túneles o puertos).
     * Solo orígenes explícitos: nunca se usa el comodín {@code *} porque las
     * peticiones van con credenciales.
     */
    @Bean
    WebMvcConfigurer panelCorsConfigurer(
            @Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
            @Value("${nexus.allowed-dev-origins:${NEXUS_ALLOWED_DEV_ORIGINS:}}") String allowedDevOrigins
    ) {
        String[] allowedOrigins = resolveAllowedOrigins(frontendBaseUrl, allowedDevOrigins);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/panel/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }

    private static String[] resolveAllowedOrigins(String frontendBaseUrl, String allowedDevOrigins) {
        List<String> origins = new ArrayList<>();
        addIfPresent(origins, frontendBaseUrl);
        if (allowedDevOrigins != null && !allowedDevOrigins.isBlank()) {
            for (String raw : allowedDevOrigins.split(",")) {
                addIfPresent(origins, raw);
            }
        }
        return origins.toArray(new String[0]);
    }

    private static void addIfPresent(List<String> origins, String rawOrigin) {
        if (rawOrigin == null) {
            return;
        }
        String normalized = normalizeBaseUrl(rawOrigin.trim());
        if (!normalized.isEmpty() && !origins.contains(normalized)) {
            origins.add(normalized);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
