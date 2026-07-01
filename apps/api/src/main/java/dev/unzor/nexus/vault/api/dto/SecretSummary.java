package dev.unzor.nexus.vault.api.dto;

import dev.unzor.nexus.vault.domain.entity.ProjectSecret;

import java.time.Instant;
import java.util.UUID;

/** Vista de un secreto: metadatos sólo, el valor plano nunca se expone. */
public record SecretSummary(
        UUID id,
        String key,
        String cipher,
        Instant createdAt,
        Instant updatedAt,
        Instant lastRotatedAt
) {
    public static SecretSummary from(ProjectSecret secret) {
        return new SecretSummary(
                secret.getId(),
                secret.getKey(),
                secret.getCipher(),
                secret.getCreatedAt(),
                secret.getUpdatedAt(),
                secret.getLastRotatedAt()
        );
    }
}
