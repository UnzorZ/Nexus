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

    @Column(name = "stale_after_seconds", nullable = false)
    private int staleAfterSeconds;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectRegistrySettings(UUID projectId, int intervalSeconds, int staleAfterSeconds, int timeoutSeconds) {
        this.projectId = projectId;
        this.intervalSeconds = intervalSeconds;
        this.staleAfterSeconds = staleAfterSeconds;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void update(int intervalSeconds, int staleAfterSeconds, int timeoutSeconds) {
        this.intervalSeconds = intervalSeconds;
        this.staleAfterSeconds = staleAfterSeconds;
        this.timeoutSeconds = timeoutSeconds;
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
