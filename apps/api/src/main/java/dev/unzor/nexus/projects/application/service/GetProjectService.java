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

    public GetProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public ProjectDetails getById(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(ProjectDetails::from)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
    }
}
