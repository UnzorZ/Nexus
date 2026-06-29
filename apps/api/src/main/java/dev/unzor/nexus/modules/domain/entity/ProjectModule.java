package dev.unzor.nexus.modules.domain.entity;

import dev.unzor.nexus.modules.domain.enums.NexusModule;
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
 * Estado de habilitación de un módulo Nexus dentro de un proyecto.
 *
 * <p>La ausencia de fila en persistencia implica el valor por defecto del catálogo;
 * esta entidad solo materializa overrides explícitos.</p>
 */
@Entity
@Table(
        name = "project_modules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_module_project_module",
                columnNames = {"project_id", "module"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectModule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NexusModule module;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectModule(UUID projectId, NexusModule module, boolean enabled) {
        this.projectId = Objects.requireNonNull(projectId);
        this.module = Objects.requireNonNull(module);
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
