package dev.unzor.nexus.admin.application.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class PanelWebConfiguration {

    @Bean
    WebMvcConfigurer panelCorsConfigurer(
            @Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        String normalizedFrontendBaseUrl = normalizeBaseUrl(frontendBaseUrl);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/panel/**")
                        .allowedOrigins(normalizedFrontendBaseUrl)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
