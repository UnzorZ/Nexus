package dev.unzor.nexus.sdk.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadatos de un secreto del vault ({@code GET /api/v1/vault/secrets}, scope
 * {@code vault:read}). El listado NO expone el valor — sólo metadatos + el
 * cifrado usado (p. ej. {@code AES_256_GCM}). Usa {@link VaultSecret#get(String)}
 * para revelar el valor.
 */
public record VaultSecretSummary(
        UUID id,
        String key,
        String cipher,
        Instant createdAt,
        Instant updatedAt,
        Instant lastRotatedAt
) {
}
