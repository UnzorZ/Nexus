package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectSlugReference;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resuelve un slug de proyecto a su identificador sin exponer repositorios fuera del módulo.
 */
@Service
public class ResolveProjectBySlugService {

    private final ProjectRepository projectRepository;

    public ResolveProjectBySlugService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public ProjectSlugReference resolve(String projectSlug) {
        return toReference(requireProject(projectSlug));
    }

    @Transactional(readOnly = true)
    public ProjectSlugReference resolveOperational(String projectSlug) {
        Project project = requireProject(projectSlug);
        if (!project.isOperational()) {
            throw new ProjectNotOperationalException(project.getId(), project.getStatus());
        }
        return toReference(project);
    }

    /**
     * Public base URL the project declared (the address of its own app), or {@code null}. It can be
     * a placeholder: callers that build links must check it against the origins the project
     * registered.
     */
    @Transactional(readOnly = true)
    public String publicBaseUrl(String projectSlug) {
        return requireProject(projectSlug).getPublicBaseUrl();
    }

    private Project requireProject(String projectSlug) {
        return projectRepository.findBySlugIgnoreCase(projectSlug)
                .orElseThrow(() -> new ProjectNotFoundException(projectSlug));
    }

    private static ProjectSlugReference toReference(Project project) {
        return new ProjectSlugReference(project.getId(), project.getSlug());
    }
}
