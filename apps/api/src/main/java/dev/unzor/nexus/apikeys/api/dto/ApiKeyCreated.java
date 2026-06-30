package dev.unzor.nexus.apikeys.api.dto;

import dev.unzor.nexus.apikeys.domain.entity.ProjectApiKey;
import dev.unzor.nexus.apikeys.domain.enums.ApiKeyStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta plana de crear/rotar una API key (spec §12.2): {@code secret} es el
 * secreto completo, que solo se devuelve esta vez (spec §21.1: "Display full
 * secret once"). El resto son metadatos no sensibles.
 */
public record ApiKeyCreated(
        UUID id,
        String name,
        String prefix,
        String secret,
        ApiKeyStatus status,
        List<String> scopes,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt
) {
    public static ApiKeyCreated of(ProjectApiKey key, String secret) {
        return new ApiKeyCreated(
                key.getId(),
                key.getName(),
                key.getKeyPrefix(),
                secret,
                key.getStatus(),
                key.getScopes(),
                key.getExpiresAt(),
                key.getLastUsedAt(),
                key.getCreatedAt());
    }
}

