package dev.unzor.nexus.identity.application.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Reserva rutas futuras de autenticación OAuth por proyecto bajo {@code /p/{projectSlug}/**}.
 */
@Configuration
class ProjectSecurityConfiguration {

    @Bean
    @Order(4)
    SecurityFilterChain projectSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/p/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/p/*/login",
                                "/p/*/logout",
                                "/p/*/register",
                                "/p/*/verify-email",
                                "/p/*/verify-email/**",
                                "/p/*/password-reset",
                                "/p/*/password-reset/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(401)
                        )
                );

        return http.build();
    }
}
