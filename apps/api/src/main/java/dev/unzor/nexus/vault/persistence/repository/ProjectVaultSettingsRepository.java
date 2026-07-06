package dev.unzor.nexus.vault.persistence.repository;

import dev.unzor.nexus.vault.domain.entity.ProjectVaultSettings;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectVaultSettingsRepository extends Repository<ProjectVaultSettings, UUID> {

    Optional<ProjectVaultSettings> findByProjectId(UUID projectId);

    ProjectVaultSettings save(ProjectVaultSettings settings);

    void deleteByProjectId(UUID projectId);
}
