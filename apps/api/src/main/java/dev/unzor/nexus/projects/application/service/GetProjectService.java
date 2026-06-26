package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectDetails;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso para obtener los detalles de un proyecto por su identificador.
 * La autorización de acceso se gestiona por separado en {@link ProjectAccessService}.
 */
@Service
public class GetProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;

    public GetProjectService(
            ProjectRepository projectRepository,
            ProjectAccessService projectAccessService
    ) {
        this.projectRepository = projectRepository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public ProjectDetails getById(UUID projectId, UUID accountId, boolean isInstanceAdmin) {
        return projectRepository.findById(projectId)
                .map(project -> ProjectDetails.from(
                        project,
                        projectAccessService.canManage(projectId, accountId, isInstanceAdmin),
                        projectAccessService.canDelete(projectId, accountId, isInstanceAdmin)
                ))
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
    }
}
