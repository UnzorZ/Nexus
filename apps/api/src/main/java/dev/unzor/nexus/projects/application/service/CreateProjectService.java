package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.exception.ProjectAlreadyExistException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateProjectService {

    private final ProjectRepository projectRepository;

    public CreateProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Project createProject(String slug, String name, String description, String publicBaseUrl) {
        if (projectRepository.existsBySlugIgnoreCase(slug)) {
            throw new ProjectAlreadyExistException(slug);
        }

        Project project = Project.builder()
            .slug(slug)
            .name(name)
            .description(description)
            .publicBaseUrl(publicBaseUrl)
            .build();
        return projectRepository.save(project);
    }
}