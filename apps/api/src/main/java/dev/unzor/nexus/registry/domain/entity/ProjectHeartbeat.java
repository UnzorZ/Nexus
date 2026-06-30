package dev.unzor.nexus.registry.domain.entity;

import dev.unzor.nexus.registry.domain.MetadataConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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

/**
 * Latido de una instancia de app (spec §9.11, §13.1). Cada latido lo reporta una
 * instancia autenticándose con una API key de proyecto; la identidad de la
 * instancia es {@code (projectId, instanceId)}. {@code status} es el valor que
 * reporta el cliente (por defecto "up"); la liveness ONLINE/STALE/OFFLINE se
 * deriva de {@code lastSeenAt} y el timeout, nunca se persiste.
 */
@Entity
@Table(
        name = "project_heartbeats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_heartbeats_project_instance",
                columnNames = {"project_id", "instance_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectHeartbeat {

    private static final String DEFAULT_STATUS = "up";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "api_key_id", nullable = false)
    private UUID apiKeyId;

    @Column(name = "instance_id", nullable = false, length = 128)
    private String instanceId;

    @Column(name = "app_name", nullable = false, length = 255)
    private String appName;

    @Column(name = "app_version", length = 128)
    private String appVersion;

    @Column(nullable = false, length = 32)
    private String status;

    @Convert(converter = MetadataConverter.class)
    @Column(name = "metadata_json")
    private Map<String, Object> metadata;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectHeartbeat(
            UUID projectId,
            UUID apiKeyId,
            String instanceId,
            String appName,
            String appVersion,
            String status,
            Map<String, Object> metadata,
            Instant lastSeenAt
    ) {
        this.projectId = Objects.requireNonNull(projectId);
        this.apiKeyId = Objects.requireNonNull(apiKeyId);
        this.instanceId = Objects.requireNonNull(instanceId);
        this.appName = Objects.requireNonNull(appName);
        this.appVersion = appVersion;
        this.status = (status == null || status.isBlank()) ? DEFAULT_STATUS : status;
        this.metadata = metadata;
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt);
    }

    /**
     * Actualiza los campos mutables con los datos de un nuevo latido de la misma
     * instancia (la identidad {@code projectId}/{@code instanceId} no cambia).
     * Refresca {@code apiKeyId} al reportero actual para que la rotación de la
     * clave quede reflejada.
     */
    public void touch(UUID apiKeyId, String appName, String appVersion, String status,
                      Map<String, Object> metadata, Instant lastSeenAt) {
        this.apiKeyId = Objects.requireNonNull(apiKeyId);
        this.appName = Objects.requireNonNull(appName);
        this.appVersion = appVersion;
        this.status = (status == null || status.isBlank()) ? DEFAULT_STATUS : status;
        this.metadata = metadata;
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt);
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
