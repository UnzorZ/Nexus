package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectDetails;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.exception.ProjectAlreadyExistException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso para crear un nuevo proyecto y otorgar a su creador la membresía
 * de {@link ProjectMembershipRole#OWNER OWNER}.
 */
@Service
public class CreateProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;

    public CreateProjectService(
            ProjectRepository projectRepository,
            ProjectMembershipRepository membershipRepository
    ) {
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public ProjectDetails createProject(
            String slug,
            String name,
            String description,
            String publicBaseUrl,
            UUID creatorAccountId
    ) {
        if (projectRepository.existsBySlugIgnoreCase(slug)) {
            throw new ProjectAlreadyExistException(slug);
        }

        Project project = Project.builder()
                .slug(slug)
                .name(name)
                .description(description)
                .publicBaseUrl(publicBaseUrl)
                .build();

        Project savedProject;
        try {
            // saveAndFlush materializa el INSERT dentro del bloque para que la
            // violación de unicidad del slug se lance aquí, no al hacer commit.
            savedProject = projectRepository.saveAndFlush(project);
        } catch (DataIntegrityViolationException exception) {
            // La única restricción única de la tabla projects es el slug
            // (case-insensitive vía uk_projects_slug_lower); cualquier violación
            // de unicidad en este INSERT se debe a un slug duplicado.
            throw new ProjectAlreadyExistException(slug);
        }

        ProjectMembership membership = new ProjectMembership(
                savedProject.getId(),
                creatorAccountId,
                ProjectMembershipRole.OWNER
        );
        membershipRepository.save(membership);

        return ProjectDetails.from(savedProject);
    }
}
