package dev.unzor.nexus.registry.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Umbrales de liveness de heartbeat de un proyecto (una fila por proyecto). Si
 * no existe, se usan los defaults globales ({@code nexus.registry.heartbeat.*}).
 */
@Entity
@Table(name = "project_registry_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectRegistrySettings {

    @Id
    private UUID projectId;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    /** Toggle de alerta offline (heartbeat → notify). */
    @Column(name = "offline_notify_enabled", nullable = false)
    private boolean offlineNotifyEnabled;

    /** Destinatario de la alerta offline (null si desactivado). */
    @Column(name = "offline_notify_email", length = 320)
    private String offlineNotifyEmail;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectRegistrySettings(UUID projectId, int intervalSeconds, int timeoutSeconds) {
        this.projectId = projectId;
        this.intervalSeconds = intervalSeconds;
        this.timeoutSeconds = timeoutSeconds;
        this.offlineNotifyEnabled = false;
        this.offlineNotifyEmail = null;
    }

    public void update(int intervalSeconds, int timeoutSeconds) {
        this.intervalSeconds = intervalSeconds;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Actualiza sólo la config de alerta offline (independiente de los umbrales). */
    public void updateOfflineNotify(boolean offlineNotifyEnabled, String offlineNotifyEmail) {
        this.offlineNotifyEnabled = offlineNotifyEnabled;
        this.offlineNotifyEmail = offlineNotifyEmail;
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
