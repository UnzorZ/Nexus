package dev.unzor.nexus.sdk.security;

import dev.unzor.nexus.sdk.NexusProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
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
@EnableConfigurationProperties(NexusProperties.class)
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
        // El endpoint de back-channel logout recibe POSTs de Nexus SIN sesión ni
        // token CSRF: debe ser público y estar excluido de CSRF (acotado a su path).
        String backchannelPath = properties.getSecurity().getBackchannelLogoutPath();
        java.util.List<String> permitAll = new java.util.ArrayList<>(properties.getSecurity().getPublicPaths());
        permitAll.add(backchannelPath);
        http.authorizeHttpRequests(a -> a
                        .requestMatchers(permitAll.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(backchannelPath))
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository)));
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    public ClientRegistrationRepository nexusClientRegistrationRepository(NexusProperties properties) {
        NexusProperties.Security sec = properties.getSecurity();
        // fromIssuerLocation espera el issuer "pelado": él mismo añade
        // /.well-known/openid-configuration y valida que el claim `issuer` del
        // metadata coincida. Pasarle la URL well-known completa rompe esa validación.
        ClientRegistration registration = ClientRegistrations.fromIssuerLocation(sec.getIssuer())
                .registrationId("nexus")
                .clientId(sec.getClient().getClientId())
                .clientSecret(sec.getClient().getClientSecret())
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean("perm")
    public NexusPermissionService nexusPermissionService() {
        return new NexusPermissionService();
    }

    /**
     * Decoder JWT compartido: valida los access tokens (resource server) y los
     * logout tokens back-channel (firma RS256 contra el JWKS del realm).
     * Importante: valida el {@code iss} contra el issuer configurado — todos los
     * realms comparten la clave de firma, así que sin esta comprobación un token
     * válido emitido por OTRO proyecto autenticaría contra esta app.
     */
    @Bean
    public JwtDecoder nexusJwtDecoder(NexusProperties properties) {
        String issuer = properties.getSecurity().getIssuer();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/oauth2/jwks").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
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
