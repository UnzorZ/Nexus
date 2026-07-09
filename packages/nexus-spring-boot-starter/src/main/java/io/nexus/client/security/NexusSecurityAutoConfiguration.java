package io.nexus.client.security;

import io.nexus.client.NexusProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Autoconfiguración de la <b>mitad de seguridad</b> del starter: resource server
 * JWT/introspect + cliente OIDC (login) + RP-initiated logout + bean {@code @perm}.
 * Se activa cuando se configura {@code nexus.security.issuer}. Genera el
 * {@link ClientRegistration} desde el discovery de Nexus (no hace falta
 * {@code spring.security.oauth2.client.*} en YAML).
 *
 * <p>Dos cadenas: (1) API/resource server ({@code Order(1)}, matchea
 * {@code nexus.security.api-paths}, STATELESS) y (2) cliente OIDC ({@code Order(2)},
 * login + logout) como fallback.</p>
 */
@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty("nexus.security.issuer")
public class NexusSecurityAutoConfiguration {

    // --- Resource server: JWT (default) -------------------------------------

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "nexus.security.rs-mode", havingValue = "jwt", matchIfMissing = true)
    public SecurityFilterChain nexusJwtApiFilterChain(HttpSecurity http, NexusProperties properties,
                                                      JwtDecoder jwtDecoder) throws Exception {
        http.securityMatcher(properties.getSecurity().getApiPaths().toArray(String[]::new))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(new NexusJwtAuthenticationConverter())))
                .csrf(CsrfConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "nexus.security.rs-mode", havingValue = "introspect")
    public SecurityFilterChain nexusIntrospectApiFilterChain(HttpSecurity http, NexusProperties properties) throws Exception {
        NexusProperties.Security sec = properties.getSecurity();
        http.securityMatcher(sec.getApiPaths().toArray(String[]::new))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.opaqueToken(o -> o
                        .introspectionUri(sec.getIssuer() + "/oauth2/introspect")
                        .introspectionClientCredentials(sec.getClient().getClientId(), sec.getClient().getClientSecret())))
                .csrf(CsrfConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    // --- OIDC client (login + RP logout) ------------------------------------

    @Bean
    @Order(2)
    public SecurityFilterChain nexusClientFilterChain(HttpSecurity http, NexusProperties properties,
                                                       ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        String[] publicPaths = properties.getSecurity().getPublicPaths().toArray(String[]::new);
        http.authorizeHttpRequests(a -> a
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository)));
        return http.build();
    }

    @Bean
    public ClientRegistration nexusClientRegistration(NexusProperties properties) {
        NexusProperties.Security sec = properties.getSecurity();
        String discovery = sec.getIssuer() + "/.well-known/openid-configuration";
        return ClientRegistrations.fromIssuerLocation(discovery)
                .registrationId("nexus")
                .clientId(sec.getClient().getClientId())
                .clientSecret(sec.getClient().getClientSecret())
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile")
                .build();
    }

    @Bean("perm")
    public NexusPermissionService nexusPermissionService() {
        return new NexusPermissionService();
    }

    /**
     * Decoder JWT compartido: valida los access tokens (resource server) y los
     * logout tokens back-channel (firma RS256 contra el JWKS del realm).
     */
    @Bean
    public JwtDecoder nexusJwtDecoder(NexusProperties properties) {
        return NimbusJwtDecoder.withJwkSetUri(properties.getSecurity().getIssuer() + "/oauth2/jwks").build();
    }

    @Bean
    public NexusBackChannelLogoutController nexusBackChannelLogoutController(JwtDecoder jwtDecoder,
                                                                             NexusProperties properties,
                                                                             ApplicationEventPublisher publisher) {
        return new NexusBackChannelLogoutController(jwtDecoder, properties, publisher);
    }

    private static LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository repo) {
        OidcClientInitiatedLogoutSuccessHandler handler = new OidcClientInitiatedLogoutSuccessHandler(repo);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}
