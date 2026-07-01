package dev.unzor.nexus.notify.domain.entity;

import dev.unzor.nexus.notify.domain.NotifyVariablesConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Variables globales de notificación de un proyecto (una fila por proyecto). Se
 * aplican a TODOS los correos y pueden sobrescribirse por envío.
 */
@Entity
@Table(name = "project_notify_variables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectNotifyVariables {

    @Id
    private UUID projectId;

    @Convert(converter = NotifyVariablesConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, String> variables;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectNotifyVariables(UUID projectId, Map<String, String> variables) {
        this.projectId = projectId;
        this.variables = variables == null ? Map.of() : variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables == null ? Map.of() : variables;
    }

    @PrePersist
    void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
