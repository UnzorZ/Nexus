package dev.unzor.nexus.notify.domain.entity;

import dev.unzor.nexus.notify.domain.NotifyVariablesConverter;
import dev.unzor.nexus.notify.domain.enums.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "notification_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_templates_project_name",
                columnNames = {"project_id", "name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;

    /** Variables declaradas (nombre -> valor por defecto) usadas en el cuerpo. */
    @Convert(converter = NotifyVariablesConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, String> variables;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationTemplate(UUID projectId, String name, NotificationChannel channel,
                                 String subject, String bodyTemplate, Map<String, String> variables) {
        this.projectId = Objects.requireNonNull(projectId);
        this.name = Objects.requireNonNull(name);
        this.channel = Objects.requireNonNull(channel);
        this.subject = Objects.requireNonNull(subject);
        this.bodyTemplate = Objects.requireNonNull(bodyTemplate);
        this.variables = variables == null ? Map.of() : variables;
    }

    public void rewrite(String name, String subject, String bodyTemplate, Map<String, String> variables) {
        this.name = Objects.requireNonNull(name);
        this.subject = Objects.requireNonNull(subject);
        this.bodyTemplate = Objects.requireNonNull(bodyTemplate);
        this.variables = variables == null ? Map.of() : variables;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
