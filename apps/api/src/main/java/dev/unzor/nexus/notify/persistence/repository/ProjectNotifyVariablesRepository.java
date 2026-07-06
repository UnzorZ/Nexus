package dev.unzor.nexus.notify.persistence.repository;

import dev.unzor.nexus.notify.domain.entity.ProjectNotifyVariables;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectNotifyVariablesRepository extends Repository<ProjectNotifyVariables, UUID> {

    Optional<ProjectNotifyVariables> findByProjectId(UUID projectId);

    ProjectNotifyVariables save(ProjectNotifyVariables variables);
}
