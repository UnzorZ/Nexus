package dev.unzor.nexus.permissions.persistence.repository;

import dev.unzor.nexus.permissions.domain.entity.ProjectUserRole;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Acceso a persistencia para las asignaciones usuario-de-proyecto → rol. La
 * operación de reemplazo completo ({@link #deleteByProjectIdAndUserId}) soporta
 * la semántica PUT del endpoint de asignación de roles a un usuario.
 */
public interface ProjectUserRoleRepository extends Repository<ProjectUserRole, UUID> {

    ProjectUserRole save(ProjectUserRole assignment);

    List<ProjectUserRole> findAllByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectUserRole> findAllByProjectIdAndRoleId(UUID projectId, UUID roleId);

    boolean existsByProjectIdAndUserIdAndRoleId(UUID projectId, UUID userId, UUID roleId);

    /**
     * Borrado bulk (DML directo) de todas las asignaciones de un usuario: ejecuta
     * el {@code DELETE} al instante, antes de cualquier inserción, para que el
     * reemplazo (PUT) no choque con la restricción única
     * {@code (project_user_id, role_id)} cuando el nuevo conjunto solapa con el
     * anterior. Un delete derivado (load-then-remove) se difiere al flush y
     * Hibernate inserta antes de borrar, lo que rompería la restricción única.
     */
    @Modifying
    @Query("delete from ProjectUserRole a where a.projectId = :projectId and a.userId = :userId")
    void deleteByProjectIdAndUserId(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}
