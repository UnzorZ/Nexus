package dev.unzor.nexus.identity.api.dto;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vista no sensible de un cliente OAuth de proyecto. Nunca expone el hash del
 * secreto (sólo si el cliente es confidencial).
 */
public record OauthClientSummary(
        UUID id,
        String clientId,
        String name,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        List<String> grantTypes,
        List<String> scopes,
        boolean requirePkce,
        boolean consentRequired,
        boolean confidential,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static OauthClientSummary from(ProjectOauthClient client) {
        return new OauthClientSummary(
                client.getId(),
                client.getClientId(),
                client.getName(),
                client.getRedirectUris(),
                client.getPostLogoutRedirectUris(),
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
