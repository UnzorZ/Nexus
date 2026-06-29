package dev.unzor.nexus.permissions.domain.entity;

import jakarta.persistence.Column;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Rol definido por el usuario dentro de un proyecto (spec §9.8). Un rol agrupa
 * un conjunto de claves de permiso (ver {@link ProjectRolePermission}) que, en
 * el milestone de identity, se asignarán a {@code ProjectUser}.
 *
 * <p>La columna {@code system} se reserva para futuros roles del sistema; en el
 * MVP no se siembra ninguno y siempre es {@code false}.</p>
 */
@Entity
@Table(
        name = "project_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_roles_project_key",
                columnNames = {"project_id", "key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectRole {

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

    @Column(nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectRole(UUID projectId, String key, String label, String description) {
        this.projectId = Objects.requireNonNull(projectId);
        this.key = Objects.requireNonNull(key);
        this.label = Objects.requireNonNull(label);
        this.description = description;
        this.system = false;
    }

    /**
     * Cambia la etiqueta y descripción mostradas. La clave y el flag
     * {@code system} son inmutables desde la API.
     */
    public void relabel(String label, String description) {
        this.label = Objects.requireNonNull(label);
        this.description = description;
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
