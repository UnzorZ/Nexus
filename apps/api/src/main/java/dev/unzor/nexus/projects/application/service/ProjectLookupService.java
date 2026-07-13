package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolución de proyectos para otros módulos sin exponer el repositorio JPA.
 */
@Service
public class ProjectLookupService {

    private final ProjectRepository projectRepository;
    private final EntityManager entityManager;

    public ProjectLookupService(ProjectRepository projectRepository, EntityManager entityManager) {
        this.projectRepository = projectRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Project requireById(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
    }

    /**
     * Exige que el proyecto exista y admita operaciones runtime sin exponer su entidad.
     */
    @Transactional(readOnly = true)
    public void requireOperationalById(UUID projectId) {
        Project project = requireById(projectId);
        if (!project.isOperational()) {
            throw new ProjectNotOperationalException(projectId, project.getStatus());
        }
    }

    /**
     * Acquires a shared database lock on an operational project. OAuth
     * authorization persistence calls this inside its write transaction so an
     * archive cannot pass its status update and bulk revocation concurrently.
     */
    @Transactional
    public void lockOperationalById(UUID projectId) {
        Project project = projectRepository.findForShareById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
        // A previous lookup may already have loaded this aggregate into the current
        // persistence context before the shared lock was acquired. Refresh under the
        // lock so a concurrent archive committed while we waited cannot leave us
        // validating the stale ACTIVE instance from the first-level cache.
        entityManager.refresh(project, LockModeType.PESSIMISTIC_READ);
        if (!project.isOperational()) {
            throw new ProjectNotOperationalException(projectId, project.getStatus());
        }
    }

    /**
     * Slug del proyecto (lanza 404 si no existe). Expone solo el primitivo para
     * que otros módulos no dependan de la entidad {@link Project} (Modulith).
     */
    @Transactional(readOnly = true)
    public String requireSlug(UUID projectId) {
        return requireById(projectId).getSlug();
    }

    /**
     * Si el proyecto tiene habilitado el registro público (self-signup). Expone solo
     * el primitivo para que otros módulos no dependan de la entidad {@link Project}.
     */
    @Transactional(readOnly = true)
    public boolean isPublicRegistrationEnabled(UUID projectId) {
        return requireById(projectId).isPublicRegistrationEnabled();
    }

}
