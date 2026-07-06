package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.identity.application.configuration.NexusOAuthBootstrapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OidcRegisteredClientBootstrapTests {

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private NexusOAuthBootstrapProperties bootstrapProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void bootstrapsStableOidcClient() {
        RegisteredClient client =
                registeredClientRepository.findByClientId(bootstrapProperties.clientId());

        assertThat(client).isNotNull();
        assertThat(client.getId()).isEqualTo(bootstrapProperties.registeredClientId());
        // El bean PasswordEncoder es BCrypt plano, así que el client_secret se almacena sin
        // el prefijo "{bcrypt}" (con prefijo, SAS no lo reconoce -> invalid_client).
        assertThat(client.getClientSecret()).startsWith("$2");
        assertThat(passwordEncoder.matches(bootstrapProperties.clientSecret(), client.getClientSecret()))
                .isTrue();
    }

    @Test
    void leavesTheSecretUnchangedWhenConfigurationAlreadyMatches() {
        RegisteredClient existing =
                registeredClientRepository.findByClientId(bootstrapProperties.clientId());

        new OidcRegisteredClientBootstrap(
                registeredClientRepository,
                bootstrapProperties,
                passwordEncoder
        ).run(null);

        RegisteredClient unchanged =
                registeredClientRepository.findByClientId(bootstrapProperties.clientId());
        assertThat(unchanged.getClientSecret()).isEqualTo(existing.getClientSecret());
    }

    @Test
    void reconcilesChangedBootstrapConfigurationWithoutChangingTheDatabaseId() {
        RegisteredClient existing =
                registeredClientRepository.findByClientId(bootstrapProperties.clientId());
        NexusOAuthBootstrapProperties changedProperties = new NexusOAuthBootstrapProperties(
                "ignored-new-database-id",
                bootstrapProperties.clientId(),
                "rotated-secret",
                "http://127.0.0.1:8080/oauth2/bootstrap/rotated-callback",
                "http://localhost:3000/rotated-logout"
        );
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        OidcRegisteredClientBootstrap bootstrap = new OidcRegisteredClientBootstrap(
                registeredClientRepository,
                changedProperties,
                passwordEncoder
        );

        try {
            bootstrap.run(null);

            RegisteredClient updated =
                    registeredClientRepository.findByClientId(bootstrapProperties.clientId());
            assertThat(updated.getId()).isEqualTo(existing.getId());
            assertThat(updated.getRedirectUris()).containsExactly(changedProperties.redirectUri());
            assertThat(updated.getPostLogoutRedirectUris())
                    .containsExactly(changedProperties.postLogoutRedirectUri());
            assertThat(passwordEncoder.matches(
                    changedProperties.clientSecret(),
                    updated.getClientSecret()
            )).isTrue();
        }
        finally {
            new OidcRegisteredClientBootstrap(
                    registeredClientRepository,
                    bootstrapProperties,
                    passwordEncoder
            ).run(null);
        }
    }

    @Test
    void rejectsChangingClientIdForAnExistingDatabaseId() {
        NexusOAuthBootstrapProperties changedProperties = new NexusOAuthBootstrapProperties(
                bootstrapProperties.registeredClientId(),
                "changed-client-id",
                bootstrapProperties.clientSecret(),
                bootstrapProperties.redirectUri(),
                bootstrapProperties.postLogoutRedirectUri()
        );

        assertThatThrownBy(() -> new OidcRegisteredClientBootstrap(
                        registeredClientRepository,
                        changedProperties,
                        passwordEncoder
                ).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-id cannot be changed")
                .hasMessageContaining(bootstrapProperties.registeredClientId());
    }
}
