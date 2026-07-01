package dev.unzor.nexus.notify.domain.entity;

import dev.unzor.nexus.notify.domain.enums.NotificationChannel;
import dev.unzor.nexus.notify.domain.enums.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(nullable = false, length = 320)
    private String recipient;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(length = 500)
    private String error;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Notification(UUID projectId, NotificationChannel channel, String recipient,
                        UUID templateId, String subject, String body) {
        this.projectId = Objects.requireNonNull(projectId);
        this.channel = Objects.requireNonNull(channel);
        this.recipient = Objects.requireNonNull(recipient);
        this.templateId = templateId;
        this.subject = Objects.requireNonNull(subject);
        this.body = Objects.requireNonNull(body);
        this.status = NotificationStatus.PENDING;
    }

    public void markSent(Instant at) {
        this.status = NotificationStatus.SENT;
        this.sentAt = at;
        this.error = null;
    }

    public void markFailed(String error) {
        this.status = NotificationStatus.FAILED;
        this.error = error;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
