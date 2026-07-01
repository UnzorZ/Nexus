package dev.unzor.nexus.config.persistence.repository;

import dev.unzor.nexus.config.domain.entity.ProjectConfig;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para la configuración de los proyectos. No extiende
 * {@code JpaRepository} para no exponer {@code findById} global; las búsquedas
 * siempre incluyen el proyecto.
 */
public interface ProjectConfigRepository extends Repository<ProjectConfig, UUID> {

    ProjectConfig save(ProjectConfig config);

    ProjectConfig saveAndFlush(ProjectConfig config);

    List<ProjectConfig> findAllByProjectId(UUID projectId);

    Optional<ProjectConfig> findByProjectIdAndKey(UUID projectId, String key);

    boolean existsByProjectIdAndKey(UUID projectId, String key);

    void delete(ProjectConfig config);
}
