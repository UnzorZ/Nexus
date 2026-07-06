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
 * Asignación de un {@code ProjectUser} (end-user del realm de un proyecto) a un
 * rol de ese proyecto (spec §9.8). Un usuario puede tener varias filas (varios
 * roles); la unión de los permisos de sus roles forma sus authorities efectivas.
 *
 * <p>{@code project_user_id} es un UUID tipado <b>sin FK</b> a
 * {@code project_users} (entidad del módulo {@code identity}): la regla
 * cross-module de AGENTS.md prohíbe FK a tablas de otro módulo. {@code role_id}
 * sí hace FK a {@code project_roles} con {@code ON DELETE CASCADE}, igual que
 * {@link ProjectRolePermission}.</p>
 */
@Entity
@Table(
        name = "project_user_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_user_roles_user_role",
                columnNames = {"project_user_id", "role_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "project_user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProjectUserRole(UUID projectId, UUID userId, UUID roleId) {
        this.projectId = Objects.requireNonNull(projectId);
        this.userId = Objects.requireNonNull(userId);
        this.roleId = Objects.requireNonNull(roleId);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
