package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.NexusOAuthBootstrapProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Bootstrap idempotente del cliente OAuth técnico persistido en PostgreSQL.
 *
 * <p>No está conectado al panel Next.js. La redirect URI apunta a un callback de
 * bootstrap en la API hasta que exista un consumidor OAuth real por proyecto.</p>
 */
@Component
public class OidcRegisteredClientBootstrap implements ApplicationRunner {

    private static final String BCRYPT_PREFIX = "{bcrypt}";

    private final RegisteredClientRepository registeredClientRepository;
    private final NexusOAuthBootstrapProperties properties;
    private final PasswordEncoder passwordEncoder;

    public OidcRegisteredClientBootstrap(
            RegisteredClientRepository registeredClientRepository,
            NexusOAuthBootstrapProperties properties,
            PasswordEncoder passwordEncoder
    ) {
        this.registeredClientRepository = registeredClientRepository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        RegisteredClient existingClient = findExistingClient();
        if (existingClient == null) {
            registeredClientRepository.save(buildClient(
                    properties.registeredClientId(),
                    encodeClientSecret(properties.clientSecret())
            ));
            return;
        }

        String clientSecret = existingClient.getClientSecret();
        if (!matchesClientSecret(clientSecret)) {
            clientSecret = encodeClientSecret(properties.clientSecret());
        }

        if (matchesConfiguration(existingClient, clientSecret)) {
            return;
        }

        registeredClientRepository.save(buildClient(existingClient.getId(), clientSecret));
    }

    private RegisteredClient findExistingClient() {
        RegisteredClient clientByClientId =
                registeredClientRepository.findByClientId(properties.clientId());
        if (clientByClientId != null) {
            return clientByClientId;
        }

        RegisteredClient clientByDatabaseId =
                registeredClientRepository.findById(properties.registeredClientId());
        if (clientByDatabaseId != null) {
            throw new IllegalStateException(
                    (
                            "OAuth bootstrap client-id cannot be changed from '%s' to '%s' "
                                    + "while registered-client-id remains '%s'."
                    )
                            .formatted(
                                    clientByDatabaseId.getClientId(),
                                    properties.clientId(),
                                    properties.registeredClientId()
                            )
            );
        }

        return null;
    }

    private RegisteredClient buildClient(String registeredClientId, String clientSecret) {
        return RegisteredClient.withId(registeredClientId)
                .clientId(properties.clientId())
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(properties.redirectUri())
                .postLogoutRedirectUri(properties.postLogoutRedirectUri())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build();
    }

    private boolean matchesConfiguration(RegisteredClient client, String expectedSecret) {
        return expectedSecret.equals(client.getClientSecret())
                && client.getClientAuthenticationMethods().equals(
                        Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                )
                && client.getAuthorizationGrantTypes().equals(
                        Set.of(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN)
                )
                && client.getRedirectUris().equals(Set.of(properties.redirectUri()))
                && client.getPostLogoutRedirectUris().equals(
                        Set.of(properties.postLogoutRedirectUri())
                )
                && client.getScopes().equals(Set.of(OidcScopes.OPENID, OidcScopes.PROFILE))
                && client.getClientSettings().isRequireAuthorizationConsent();
    }

    private String encodeClientSecret(String rawSecret) {
        return BCRYPT_PREFIX + passwordEncoder.encode(rawSecret);
    }

    private boolean matchesClientSecret(String encodedSecret) {
        if (encodedSecret == null || !encodedSecret.startsWith(BCRYPT_PREFIX)) {
            return false;
        }
        return passwordEncoder.matches(
                properties.clientSecret(),
                encodedSecret.substring(BCRYPT_PREFIX.length())
        );
    }
}
