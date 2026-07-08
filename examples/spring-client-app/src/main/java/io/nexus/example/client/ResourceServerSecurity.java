package io.nexus.example.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server security chains for {@code /api/**}. Exactly one of the two
 * beans is active, selected by {@code nexus.rs.mode}:
 *
 * <ul>
 *   <li>{@code jwt} (default) — validate the access token locally (signature +
 *       exp against the project JWKS). Fast, but a role revocation is only
 *       honored once the token's {@code exp} passes.</li>
 *   <li>{@code introspect} — validate each request via {@code /oauth2/introspect}.
 *       Honors {@code authz_version} revocation immediately, at the cost of a
 *       server round-trip per request.</li>
 * </ul>
 *
 * <p>Both chains are stateless (no session, CSRF disabled) and apply
 * {@link NexusJwtAuthenticationConverter} (scope → authority). Permission-key
 * authorization is enforced in controllers via {@link PermissionService}
 * ({@code @PreAuthorize}).</p>
 */
@Configuration
public class ResourceServerSecurity {

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "nexus.rs.mode", havingValue = "jwt", matchIfMissing = true)
    public SecurityFilterChain jwtApiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(new NexusJwtAuthenticationConverter())))
                .csrf(CsrfConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "nexus.rs.mode", havingValue = "introspect")
    public SecurityFilterChain introspectApiFilterChain(HttpSecurity http) throws Exception {
        // Opaque-token introspection URI/client come from
        // spring.security.oauth2.resourceserver.opaquetoken.* (see application.yml).
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.opaqueToken(Customizer.withDefaults()))
                .csrf(CsrfConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
