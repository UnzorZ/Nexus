package dev.unzor.nexus.vault.persistence.repository;

import dev.unzor.nexus.vault.domain.entity.ProjectSecret;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectSecretRepository extends Repository<ProjectSecret, UUID> {
    ProjectSecret save(ProjectSecret secret);
    ProjectSecret saveAndFlush(ProjectSecret secret);
    List<ProjectSecret> findAllByProjectId(UUID projectId);
    Optional<ProjectSecret> findByProjectIdAndKey(UUID projectId, String key);
    boolean existsByProjectIdAndKey(UUID projectId, String key);
    void delete(ProjectSecret secret);
}
