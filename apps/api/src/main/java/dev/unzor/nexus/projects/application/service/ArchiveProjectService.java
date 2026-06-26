package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso para archivar un proyecto de forma reversible (estado ARCHIVED).
 */
@Service
public class ArchiveProjectService {

    private final ProjectRepository projectRepository;

    public ArchiveProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void archive(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
        // Idempotent: an already-archived project has nothing to do (and avoids a
        // pointless write that would only bump updatedAt).
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            return;
        }
        project.archive();
        projectRepository.save(project);
    }
}
