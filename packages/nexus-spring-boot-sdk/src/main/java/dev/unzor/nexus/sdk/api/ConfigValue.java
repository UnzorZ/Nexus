package dev.unzor.nexus.sdk.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Valor de configuración de un proyecto ({@code GET /api/v1/config/values},
 * scope {@code config:read}). El {@code value} viene en claro (no es un
 * secreto); el {@code valueType} es {@code STRING|NUMBER|BOOLEAN|JSON}.
 */
public record ConfigValue(
        UUID id,
        String key,
        String value,
        String valueType,
        Instant createdAt,
        Instant updatedAt
) {
}
