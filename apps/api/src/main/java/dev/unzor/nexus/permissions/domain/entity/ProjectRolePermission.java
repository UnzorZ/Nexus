package dev.unzor.nexus.permissions.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Concesión de una clave de permiso a un rol (spec §9.8).
 *
 * <p>Referencia la <em>clave</em> del permiso (no su id) para soportar comodines
 * como {@code orders.*} o {@code *}, que no existen como filas en el catálogo.
 * Por eso no hay FK a {@code project_permissions}: borrar un permiso declarado
 * no rompe los roles que lo referencian.</p>
 */
@Entity
@Table(
        name = "project_role_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_role_permissions_role_key",
                columnNames = {"role_id", "permission_key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_key", nullable = false, length = 128)
    private String permissionKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProjectRolePermission(UUID projectId, UUID roleId, String permissionKey) {
        this.projectId = Objects.requireNonNull(projectId);
        this.roleId = Objects.requireNonNull(roleId);
        this.permissionKey = Objects.requireNonNull(permissionKey);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
