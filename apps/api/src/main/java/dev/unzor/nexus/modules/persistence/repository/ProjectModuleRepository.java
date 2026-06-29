package dev.unzor.nexus.modules.persistence.repository;

import dev.unzor.nexus.modules.domain.entity.ProjectModule;
import dev.unzor.nexus.modules.domain.enums.NexusModule;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia del registro de módulos habilitados por proyecto.
 */
public interface ProjectModuleRepository extends Repository<ProjectModule, UUID> {

    List<ProjectModule> findAllByProjectId(UUID projectId);

    Optional<ProjectModule> findByProjectIdAndModule(UUID projectId, NexusModule module);

    ProjectModule save(ProjectModule projectModule);
}
