package dev.unzor.nexus.notify.api.dto;

import dev.unzor.nexus.notify.domain.entity.NotificationTemplate;
import dev.unzor.nexus.notify.domain.enums.NotificationChannel;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationTemplateSummary(
        UUID id,
        String name,
        NotificationChannel channel,
        String subject,
        String bodyTemplate,
        Map<String, String> variables,
        Instant createdAt,
        Instant updatedAt
) {
    public static NotificationTemplateSummary from(NotificationTemplate template) {
        return new NotificationTemplateSummary(
                template.getId(),
                template.getName(),
                template.getChannel(),
                template.getSubject(),
                template.getBodyTemplate(),
                template.getVariables(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
