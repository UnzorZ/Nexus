package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolución de proyectos para otros módulos sin exponer el repositorio JPA.
 */
@Service
public class ProjectLookupService {

    private final ProjectRepository projectRepository;

    public ProjectLookupService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public Project requireById(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
    }

    /**
     * Slug del proyecto (lanza 404 si no existe). Expone solo el primitivo para
     * que otros módulos no dependan de la entidad {@link Project} (Modulith).
     */
    @Transactional(readOnly = true)
    public String requireSlug(UUID projectId) {
        return requireById(projectId).getSlug();
    }

}
