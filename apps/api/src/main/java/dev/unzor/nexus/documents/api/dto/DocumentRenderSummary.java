package dev.unzor.nexus.documents.api.dto;

import dev.unzor.nexus.documents.domain.entity.DocumentRender;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Vista de un render histórico. */
public record DocumentRenderSummary(
        UUID id,
        String templateName,
        Map<String, String> variables,
        String output,
        Instant createdAt
) {
    public static DocumentRenderSummary from(DocumentRender render) {
        return new DocumentRenderSummary(
                render.getId(),
                render.getTemplateName(),
                render.getVariables(),
                render.getOutput(),
                render.getCreatedAt()
        );
    }
}
