package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectDetails;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.exception.ProjectAlreadyExistException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.audit.AuditOutcome;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso para crear un nuevo proyecto y otorgar a su creador la membresía
 * de {@link ProjectMembershipRole#OWNER OWNER}.
 */
@Service
public class CreateProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CreateProjectService(
            ProjectRepository projectRepository,
            ProjectMembershipRepository membershipRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.eventPublisher = eventPublisher;
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
            // Solo tratamos como conflicto de slug las violaciones de unicidad del
            // slug (columna UNIQUE y el índice funcional LOWER). Otras violaciones
            // de integridad (NOT NULL, claves foráneas futuras, etc.) deben
            // propagarse como error de servidor, no como 409 de slug duplicado.
            if (isSlugUniqueViolation(exception)) {
                throw new ProjectAlreadyExistException(slug);
            }
            throw exception;
        }

        ProjectMembership membership = new ProjectMembership(
                savedProject.getId(),
                creatorAccountId,
                ProjectMembershipRole.OWNER
        );
        membershipRepository.save(membership);

        eventPublisher.publishEvent(AuditEvent.byAccount(
                savedProject.getId(), "project.created", "project", savedProject.getId().toString(),
                AuditOutcome.SUCCESS, creatorAccountId, Map.of("slug", slug, "name", name)));

        return ProjectDetails.from(savedProject, true, true);
    }

    private static final Set<String> SLUG_UNIQUE_CONSTRAINTS =
            Set.of("projects_slug_key", "uk_projects_slug_lower");

    /**
     * Comprueba si la excepción corresponde a una violación de unicidad del slug
     * (columna UNIQUE {@code projects_slug_key} o el índice funcional
     * {@code uk_projects_slug_lower}). Otras {@code DataIntegrityViolationException}
     * (NOT NULL, claves foráneas, etc.) no se consideran conflicto de slug.
     */
    private static boolean isSlugUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && SLUG_UNIQUE_CONSTRAINTS.contains(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
