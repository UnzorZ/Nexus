package dev.unzor.nexus.notify.api.dto;

import dev.unzor.nexus.notify.domain.entity.Notification;
import dev.unzor.nexus.notify.domain.enums.NotificationChannel;
import dev.unzor.nexus.notify.domain.enums.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationSummary(
        UUID id,
        NotificationChannel channel,
        String recipient,
        String subject,
        NotificationStatus status,
        String error,
        Instant sentAt,
        Instant createdAt
) {
    public static NotificationSummary from(Notification notification) {
        return new NotificationSummary(
                notification.getId(),
                notification.getChannel(),
                notification.getRecipient(),
                notification.getSubject(),
                notification.getStatus(),
                notification.getError(),
                notification.getSentAt(),
                notification.getCreatedAt()
        );
    }
}
