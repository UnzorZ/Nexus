package dev.unzor.nexus.projects.persistence.repository;

import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para las relaciones entre cuentas Nexus y proyectos.
 *
 * <p>No extiende {@code JpaRepository} para evitar exponer accidentalmente
 * operaciones globales como {@code findById}. Las búsquedas de una membresía
 * concreta siempre incluyen el proyecto. La consulta por cuenta es global de
 * manera intencionada y se utiliza para listar los proyectos accesibles desde el
 * panel.</p>
 */
public interface ProjectMembershipRepository extends Repository<ProjectMembership, UUID> {

    ProjectMembership save(ProjectMembership membership);

    Optional<ProjectMembership> findByProjectIdAndId(UUID projectId, UUID membershipId);

    Optional<ProjectMembership> findByProjectIdAndNexusAccountId(
            UUID projectId,
            UUID nexusAccountId
    );

    List<ProjectMembership> findAllByNexusAccountIdAndStatus(
            UUID nexusAccountId,
            ProjectMembershipStatus status
    );

    List<ProjectMembership> findAllByProjectIdAndStatus(
            UUID projectId,
            ProjectMembershipStatus status
    );

    boolean existsByProjectIdAndNexusAccountIdAndStatus(
            UUID projectId,
            UUID nexusAccountId,
            ProjectMembershipStatus status
    );

    long countByProjectIdAndRoleAndStatus(
            UUID projectId,
            ProjectMembershipRole role,
            ProjectMembershipStatus status
    );
}
