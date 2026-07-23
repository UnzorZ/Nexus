package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.identity.application.service.GoogleIdTokenVerifier;
import dev.unzor.nexus.identity.application.service.GoogleTokenExchangeService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;

/**
 * Wires the Google federation collaborators that are not Spring-managed by their own
 * annotations: the id_token verifier (with a JWKS-backed decoder for Google's signing
 * keys) and the token-exchange HTTP client.
 *
 * <p>The {@code NimbusJwtDecoder} is built locally and passed straight into the verifier; it
 * is intentionally NOT exposed as a {@link JwtDecoder} bean, so it cannot collide with the
 * Authorization Server's own {@code jwtDecoder} bean (see {@code SecurityConfig}).</p>
 */
@Configuration
@EnableConfigurationProperties(OidcFederationProperties.class)
class OidcFederationConfiguration {

    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(OidcFederationProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.google().jwksUri())
                .build();
        return new GoogleIdTokenVerifier(decoder);
    }

    @Bean
    GoogleTokenExchangeService googleTokenExchangeService(RestClient restClient, OidcFederationProperties properties) {
        return new GoogleTokenExchangeService(restClient, properties);
    }
}
