package dev.unzor.nexus.permissions.domain.entity;

import dev.unzor.nexus.permissions.domain.enums.PermissionSource;
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
 * Permiso declarado en el catálogo de un proyecto (spec §9.7).
 *
 * <p>Las claves las define el usuario (origen {@link PermissionSource#WEB} en el
 * MVP) y se validan por formato; Nexus no impone un catálogo cerrado. Los campos
 * de sincronización ({@code deprecated}, {@code missingFromLastSync},
 * {@code lastDeclaredAt}) quedan reservados para la futura sincronización
 * declarativa de aplicaciones.</p>
 */
@Entity
@Table(
        name = "project_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_permissions_project_key",
                columnNames = {"project_id", "key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 128)
    private String key;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PermissionSource source;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean deprecated;

    @Column(name = "missing_from_last_sync", nullable = false)
    private boolean missingFromLastSync;

    @Column(name = "last_declared_at")
    private Instant lastDeclaredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectPermission(UUID projectId, String key, String label, String description) {
        this(projectId, key, label, description, PermissionSource.WEB);
    }

    /**
     * Constructor con origen explícito — usado por la sincronización declarativa
     * del SDK (spec §18) para crear permisos con origen {@link PermissionSource#CODE}.
     */
    public ProjectPermission(UUID projectId, String key, String label, String description,
                             PermissionSource source) {
        this.projectId = Objects.requireNonNull(projectId);
        this.key = Objects.requireNonNull(key);
        this.label = Objects.requireNonNull(label);
        this.description = description;
        this.source = Objects.requireNonNull(source);
        this.enabled = true;
        this.deprecated = false;
        this.missingFromLastSync = false;
    }

    /**
     * Cambia la etiqueta y descripción mostradas. La clave es inmutable.
     */
    public void relabel(String label, String description) {
        this.label = Objects.requireNonNull(label);
        this.description = description;
    }

    /**
     * Marca el permiso como declarado en este ciclo de sincronización desde una
     * aplicación ({@link PermissionSource#CODE} para los nuevos; el origen de los
     * ya existentes se respeta para no usurpar permisos creados a mano por el
     * operador). Actualiza la etiqueta si la declaración trae una, refresca
     * {@code lastDeclaredAt} y limpia {@code missingFromLastSync}.
     */
    public void syncDeclare(String label, Instant now) {
        if (label != null && !label.isBlank()) {
            this.label = label;
        }
        this.lastDeclaredAt = now;
        this.missingFromLastSync = false;
    }

    /**
     * Marca el permiso como ausente del último ciclo de sincronización (la
     * aplicación dejó de declararlo). Sólo aplica a permisos de origen
     * {@code CODE}/{@code YAML}; los {@code WEB}/{@code SYSTEM} no se ven
     * afectados (son gestionados por el operador/sistema).
     */
    public void markMissingFromSync() {
        this.missingFromLastSync = true;
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
