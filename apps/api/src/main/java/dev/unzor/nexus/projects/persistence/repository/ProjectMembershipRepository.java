package dev.unzor.nexus.projects.persistence.repository;

import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

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

    /**
     * Bloquea pesimistamente ({@code SELECT … FOR UPDATE}) las membresías de un
     * proyecto para serializar mutaciones sensibles al invariante de OWNER.
     *
     * <p>El valor de retorno puede ignorarse: se invoca por el efecto del bloqueo
     * a nivel de fila, de modo que dos transacciones concurrentes que cambian de
     * rol, eliminan o transfieren la propiedad de un mismo proyecto no puedan
     * pasar ambas el recuento de owners activos antes de que una confirme.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ProjectMembership m where m.projectId = :projectId")
    List<ProjectMembership> findForUpdateByProjectId(@Param("projectId") UUID projectId);
}
