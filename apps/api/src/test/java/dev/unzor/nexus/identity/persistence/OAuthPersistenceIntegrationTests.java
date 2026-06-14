package dev.unzor.nexus.identity.persistence;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.identity.application.configuration.NexusOAuthBootstrapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OAuthPersistenceIntegrationTests {

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private OAuth2AuthorizationConsentService authorizationConsentService;

    @Autowired
    private NexusOAuthBootstrapProperties bootstrapProperties;

    @Test
    void loadsPersistedRegisteredClientByClientId() {
        RegisteredClient client = registeredClientRepository.findByClientId(bootstrapProperties.clientId());

        assertThat(client).isNotNull();
        assertThat(client.getId()).isEqualTo(bootstrapProperties.registeredClientId());
        assertThat(client.getRedirectUris()).contains(bootstrapProperties.redirectUri());
    }

    @Test
    void savesAndReloadsAuthorization() {
        RegisteredClient client = registeredClientRepository.findByClientId(bootstrapProperties.clientId());
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "integration-access-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Set.of(OidcScopes.OPENID)
        );

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id("integration-authorization")
                .principalName("project-user@example.com")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(accessToken)
                .build();

        authorizationService.save(authorization);

        assertThat(authorizationService.findById("integration-authorization")).isNotNull();
        assertThat(authorizationService.findByToken("integration-access-token", OAuth2TokenType.ACCESS_TOKEN))
                .isNotNull();
    }

    @Test
    void unknownTokenDoesNotReturnAnotherAuthorization() {
        assertThat(authorizationService.findByToken("missing-token", OAuth2TokenType.ACCESS_TOKEN))
                .isNull();
    }

    @Test
    void savesAndReloadsAuthorizationConsent() {
        RegisteredClient client = registeredClientRepository.findByClientId(bootstrapProperties.clientId());
        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent.withId(
                        client.getId(),
                        "project-user@example.com"
                )
                .scope(OidcScopes.OPENID)
                .build();

        authorizationConsentService.save(consent);

        assertThat(authorizationConsentService.findById(client.getId(), "project-user@example.com"))
                .isNotNull();
    }
}
