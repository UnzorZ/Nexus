package dev.unzor.nexus.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra el {@link RateLimitFilter} como filtro de servlet con la máxima
 * precedencia, de modo que se evalúa antes que cualquier cadena de Spring Security
 * y pueda rechazar peticiones a los endpoints de auth pública sin coste de
 * autenticación.
 *
 * <p>El filtro se instancia con {@code new} (no es un bean) para evitar el proxy
 * CGLIB de Modulith sobre {@code GenericFilterBean}; el único registro es este
 * {@link FilterRegistrationBean}.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfiguration {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties properties, RateLimitBucketStore store) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(properties, store));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("rateLimitFilter");
        return registration;
    }
}
