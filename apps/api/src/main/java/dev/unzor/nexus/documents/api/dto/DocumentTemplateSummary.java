package dev.unzor.nexus.documents.api.dto;

import dev.unzor.nexus.documents.domain.entity.DocumentTemplate;

import java.time.Instant;
import java.util.UUID;

/** Vista de una plantilla de documento. */
public record DocumentTemplateSummary(
        UUID id,
        String name,
        String contentType,
        String templateBody,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentTemplateSummary from(DocumentTemplate template) {
        return new DocumentTemplateSummary(
                template.getId(),
                template.getName(),
                template.getContentType(),
                template.getTemplateBody(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
