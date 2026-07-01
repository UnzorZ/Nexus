package dev.unzor.nexus.registry.persistence.repository;

import dev.unzor.nexus.registry.domain.entity.ProjectRegistrySettings;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRegistrySettingsRepository extends Repository<ProjectRegistrySettings, UUID> {

    Optional<ProjectRegistrySettings> findByProjectId(UUID projectId);

    ProjectRegistrySettings save(ProjectRegistrySettings settings);
}
