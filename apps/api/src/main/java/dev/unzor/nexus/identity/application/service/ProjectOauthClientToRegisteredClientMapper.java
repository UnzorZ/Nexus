package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Traduce un {@link ProjectOauthClient} (persistencia propia, project-scoped) al
 * {@link RegisteredClient} que Spring Authorization Server consume durante los
 * flujos de autorización. Lo usa el {@code CompositeRegisteredClientRepository}.
 *
 * <p>El {@code id} del {@code RegisteredClient} es el UUID del cliente (string),
 * que es el {@code registered_client_id} almacenado en {@code oauth2_authorization}.
 * Token settings: access 10 min, refresh 7 d rotativo (spec §21.3 "short-lived").
 */
@Component
public class ProjectOauthClientToRegisteredClientMapper {

    public RegisteredClient toRegisteredClient(ProjectOauthClient client) {
        RegisteredClient.Builder builder = RegisteredClient.withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientName(client.getName());

        if (client.isConfidential()) {
            builder.clientSecret(client.getClientSecretHash());
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }

        for (String grant : client.getGrantTypes()) {
            builder.authorizationGrantType(new AuthorizationGrantType(grant));
        }
        client.getRedirectUris().forEach(builder::redirectUri);
        client.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
        client.getScopes().forEach(builder::scope);

        builder.clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(client.isConsentRequired())
                .requireProofKey(client.isRequirePkce())
                .build());

        builder.tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(10))
                .refreshTokenTimeToLive(Duration.ofDays(7))
                .reuseRefreshTokens(true)
                .build());

        return builder.build();
    }
}
