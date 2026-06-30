package dev.unzor.nexus.permissions.persistence.repository;

import dev.unzor.nexus.permissions.domain.entity.ProjectPermission;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para el catálogo de permisos de los proyectos. No
 * extiende {@code JpaRepository} para no exponer {@code findById} global; las
 * búsquedas siempre incluyen el proyecto.
 */
public interface ProjectPermissionRepository extends Repository<ProjectPermission, UUID> {

    ProjectPermission save(ProjectPermission permission);

    ProjectPermission saveAndFlush(ProjectPermission permission);

    List<ProjectPermission> findAllByProjectId(UUID projectId);

    Optional<ProjectPermission> findByProjectIdAndId(UUID projectId, UUID id);

    boolean existsByProjectIdAndKey(UUID projectId, String key);

    void delete(ProjectPermission permission);
}
