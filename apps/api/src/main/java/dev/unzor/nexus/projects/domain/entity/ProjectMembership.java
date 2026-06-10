package dev.unzor.nexus.projects.domain.entity;

import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
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
 * Relación de autorización entre una cuenta Nexus y un proyecto.
 *
 * <p>La membresía determina el alcance con el que una persona puede gestionar un
 * proyecto desde el panel. No representa una identidad autenticable y no debe
 * implementar {@code UserDetails}.</p>
 *
 * <p>Los identificadores de proyecto y cuenta se guardan como valores escalares para
 * evitar asociaciones JPA entre módulos. La combinación de ambos es única.</p>
 */
@Entity
@Table(
        name = "project_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_membership_project_account",
                columnNames = {"project_id", "nexus_account_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "nexus_account_id", nullable = false)
    private UUID nexusAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectMembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectMembershipStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectMembership(UUID projectId, UUID nexusAccountId, ProjectMembershipRole role) {
        this.projectId = Objects.requireNonNull(projectId);
        this.nexusAccountId = Objects.requireNonNull(nexusAccountId);
        this.role = Objects.requireNonNull(role);
        this.status = ProjectMembershipStatus.ACTIVE;
    }

    /**
     * Indica si la membresía concede acceso actualmente.
     */
    public boolean grantsProjectAccess() {
        return status == ProjectMembershipStatus.ACTIVE;
    }

    /**
     * Cambia las capacidades de la cuenta dentro del proyecto.
     *
     * <p>La capa de aplicación debe impedir que esta operación deje al proyecto sin
     * ningún {@link ProjectMembershipRole#OWNER OWNER} activo.</p>
     */
    public void changeRole(ProjectMembershipRole role) {
        this.role = Objects.requireNonNull(role);
    }

    /**
     * Activa una membresía pendiente o suspendida.
     */
    public void activate() {
        status = ProjectMembershipStatus.ACTIVE;
    }

    /**
     * Bloquea temporalmente el acceso al proyecto.
     */
    public void suspend() {
        status = ProjectMembershipStatus.SUSPENDED;
    }

    /**
     * Retira el acceso al proyecto.
     */
    public void revoke() {
        status = ProjectMembershipStatus.REVOKED;
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
