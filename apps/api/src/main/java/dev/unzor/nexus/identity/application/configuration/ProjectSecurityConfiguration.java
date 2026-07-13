package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.infrastructure.security.ProjectRealmIsolationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Cadena del realm OAuth por proyecto ({@code /p/{slug}/**}): authorize, token, jwks,
 * discovery, end-session… Inyecta el {@link ProjectRealmIsolationFilter} tras
 * {@link SecurityContextHolderFilter} para que una sesión de otro realm no pueda operar
 * estos endpoints (remediación de auditoría, hallazgo crítico). Los endpoints OAuth
 * ({@code /oauth2/**}) los captura la cadena AS @Order(1), que también lo instala.
 */
@Configuration
class ProjectSecurityConfiguration {

    @Bean
    @Order(4)
    SecurityFilterChain projectSecurityFilterChain(HttpSecurity http, ProjectSlugResolver slugResolver)
            throws Exception {
        http
                .securityMatcher("/p/**")
                .addFilterAfter(new ProjectRealmIsolationFilter(slugResolver), SecurityContextHolderFilter.class)
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
