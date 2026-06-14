package dev.unzor.nexus.shared.web;

import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra el {@link RedisUnavailableFilter} por delante del filtro de Spring Session.
 *
 * <p>El {@code SessionRepositoryFilter} de Spring Session carga y guarda la sesión en
 * Redis; este filtro se ejecuta antes (orden menor) para que su bloque {@code try}
 * envuelva a aquel y pueda convertir cualquier fallo de conexión con Redis en una
 * respuesta {@code 503 redis_unavailable}.</p>
 */
@Configuration
public class RedisWebConfiguration {

    /**
     * Orden del filtro de Spring Session. Spring Boot registra
     * {@code springSessionRepositoryFilter} con
     * {@code SessionRepositoryFilterConfiguration.DEFAULT_FILTER_ORDER}, que equivale a
     * {@code Ordered.HIGHEST_PRECEDENCE + 50}. Nos colocamos por delante (orden menor).
     */
    private static final int BEFORE_SESSION_REPOSITORY_FILTER = Ordered.HIGHEST_PRECEDENCE + 49;

    @Bean
    RedisUnavailableFilter redisUnavailableFilter(ObjectMapper objectMapper) {
        return new RedisUnavailableFilter(objectMapper);
    }

    @Bean
    FilterRegistrationBean<RedisUnavailableFilter> redisUnavailableFilterRegistration(
            RedisUnavailableFilter redisUnavailableFilter) {
        FilterRegistrationBean<RedisUnavailableFilter> registration = new FilterRegistrationBean<>(
                redisUnavailableFilter
        );
        registration.setOrder(BEFORE_SESSION_REPOSITORY_FILTER);
        registration.setName("redisUnavailableFilter");
        registration.setMatchAfter(false);
        return registration;
    }
}
