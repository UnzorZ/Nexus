package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectDetails;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso para actualizar los metadatos editables de un proyecto.
 * El slug y el estado no se modifican aquí.
 */
@Service
public class UpdateProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;

    public UpdateProjectService(
            ProjectRepository projectRepository,
            ProjectAccessService projectAccessService
    ) {
        this.projectRepository = projectRepository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional
    public ProjectDetails update(
            UUID projectId,
            String name,
            String description,
            String publicBaseUrl,
            UUID accountId,
            boolean isInstanceAdmin
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
        project.updateDetails(name, description, publicBaseUrl);
        projectRepository.saveAndFlush(project);
        return ProjectDetails.from(
                project,
                projectAccessService.canManage(projectId, accountId, isInstanceAdmin),
                projectAccessService.canDelete(projectId, accountId, isInstanceAdmin)
        );
    }
}
