package dev.unzor.nexus.documents.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Respuesta del render runtime: documento renderizado + metadatos. */
public record DocumentRenderResult(
        UUID renderId,
        String output,
        String contentType,
        Instant renderedAt
) {
}
