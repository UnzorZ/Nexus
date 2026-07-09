package dev.unzor.nexus.identity.api.dto;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de crear/rotar un cliente OAuth: {@code clientSecret} es el secreto
 * en claro, devuelto una sola vez (para clientes confidenciales; {@code null}
 * para clientes públicos sin secreto).
 */
public record OauthClientCreated(
        UUID id,
        String clientId,
        String clientSecret,
        String name,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        String backchannelLogoutUri,
        List<String> grantTypes,
        List<String> scopes,
        boolean requirePkce,
        boolean consentRequired,
        boolean confidential,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static OauthClientCreated of(ProjectOauthClient client, String rawSecret) {
        return new OauthClientCreated(
                client.getId(),
                client.getClientId(),
                rawSecret,
                client.getName(),
                client.getRedirectUris(),
                client.getPostLogoutRedirectUris(),
                client.getBackchannelLogoutUri(),
                client.getGrantTypes(),
                client.getScopes(),
                client.isRequirePkce(),
                client.isConsentRequired(),
                client.isConfidential(),
                client.getStatus().name(),
                client.getCreatedAt(),
                client.getUpdatedAt());
    }
}
