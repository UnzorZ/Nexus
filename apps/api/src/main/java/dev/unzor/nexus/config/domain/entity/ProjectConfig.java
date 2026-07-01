package dev.unzor.nexus.config.domain.entity;

import dev.unzor.nexus.config.domain.enums.ConfigValueType;
import jakarta.persistence.Column;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Valor de configuración de un proyecto (par clave/valor tipado). Sirve tanto
 * para configuración de aplicación como para flags booleanos.
 */
@Entity
@Table(
        name = "project_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_config_project_key",
                columnNames = {"project_id", "key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 128)
    private String key;

    @Column(nullable = false)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private ConfigValueType valueType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectConfig(UUID projectId, String key, String value, ConfigValueType valueType) {
        this.projectId = Objects.requireNonNull(projectId);
        this.key = Objects.requireNonNull(key);
        this.value = Objects.requireNonNull(value);
        this.valueType = Objects.requireNonNull(valueType);
    }

    /** Reescribe el valor (y su tipo). La clave es inmutable. */
    public void rewrite(String value, ConfigValueType valueType) {
        this.value = Objects.requireNonNull(value);
        this.valueType = Objects.requireNonNull(valueType);
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
