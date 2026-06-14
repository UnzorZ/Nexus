package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectSlugReference;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
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
        return projectRepository.findBySlugIgnoreCase(projectSlug)
                .map(project -> new ProjectSlugReference(project.getId(), project.getSlug()))
                .orElseThrow(() -> new ProjectNotFoundException(projectSlug));
    }
}
