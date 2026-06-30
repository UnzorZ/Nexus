package dev.unzor.nexus.permissions.persistence.repository;

import dev.unzor.nexus.permissions.domain.entity.ProjectRole;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para los roles de los proyectos. No extiende
 * {@code JpaRepository} para no exponer {@code findById} global; las búsquedas
 * siempre incluyen el proyecto.
 */
public interface ProjectRoleRepository extends Repository<ProjectRole, UUID> {

    ProjectRole save(ProjectRole role);

    ProjectRole saveAndFlush(ProjectRole role);

    List<ProjectRole> findAllByProjectId(UUID projectId);

    Optional<ProjectRole> findByProjectIdAndId(UUID projectId, UUID id);

    boolean existsByProjectIdAndKey(UUID projectId, String key);

    void delete(ProjectRole role);
}
