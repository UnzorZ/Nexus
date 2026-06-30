package dev.unzor.nexus.permissions.persistence.repository;

import dev.unzor.nexus.permissions.domain.entity.ProjectRolePermission;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Acceso a persistencia para las concesiones rol→permiso. La operación de
 * reemplazo completo ({@link #deleteByRoleId}) soporta la semántica PUT del
 * endpoint de asignación de permisos de un rol.
 */
public interface ProjectRolePermissionRepository extends Repository<ProjectRolePermission, UUID> {

    ProjectRolePermission save(ProjectRolePermission rolePermission);

    List<ProjectRolePermission> findAllByProjectId(UUID projectId);

    List<ProjectRolePermission> findAllByRoleId(UUID roleId);

    /**
     * Borrado bulk (DML directo) de las concesiones de un rol: ejecuta el
     * {@code DELETE} al instante, antes de cualquier inserción, para que el
     * reemplazo (PUT) no choque con la restricción única
     * {@code (role_id, permission_key)} cuando el nuevo conjunto solapa con el
     * anterior. Un delete derivado (load-then-remove) se difiere al flush y
     * Hibernate inserta antes de borrar, lo que rompería la restricción única.
     */
    @Modifying
    @Query("delete from ProjectRolePermission grant where grant.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") UUID roleId);
}
