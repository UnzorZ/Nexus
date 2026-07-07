package dev.unzor.nexus.identity.application.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS para la API JSON de usuario final ({@code /api/p/**}), consumida por el
 * dashboard Next.js con credenciales. Espejo de {@code PanelWebConfiguration}:
 * orígenes explícitos ({@code nexus.frontend-base-url} + {@code nexus.allowed-dev-origins}),
 * nunca el comodín {@code *}.
 */
@Configuration
class EndUserWebConfiguration {

    @Bean
    WebMvcConfigurer endUserCorsConfigurer(
            @Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
            @Value("${nexus.allowed-dev-origins:${NEXUS_ALLOWED_DEV_ORIGINS:}}") String allowedDevOrigins
    ) {
        String[] allowedOrigins = resolveAllowedOrigins(frontendBaseUrl, allowedDevOrigins);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/p/**")
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
