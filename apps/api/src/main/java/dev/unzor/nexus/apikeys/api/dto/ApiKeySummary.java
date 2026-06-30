package dev.unzor.nexus.apikeys.api.dto;

import dev.unzor.nexus.apikeys.domain.entity.ProjectApiKey;
import dev.unzor.nexus.apikeys.domain.enums.ApiKeyStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vista de una API key para listados. Nunca expone el secreto ni el hash; solo
 * el prefijo legible ({@code nxs_…}) a efectos de identificación.
 */
public record ApiKeySummary(
        UUID id,
        String name,
        String prefix,
        ApiKeyStatus status,
        List<String> scopes,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt
) {
    public static ApiKeySummary from(ProjectApiKey key, String displayPrefix) {
        return new ApiKeySummary(
                key.getId(),
                key.getName(),
                displayPrefix,
                key.getStatus(),
                key.getScopes(),
                key.getExpiresAt(),
                key.getLastUsedAt(),
                key.getCreatedAt()
        );
    }
}
