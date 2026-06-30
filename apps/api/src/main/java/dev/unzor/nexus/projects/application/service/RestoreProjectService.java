package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso para restaurar un proyecto archivado (vuelve a estado ACTIVE).
 *
 * <p>Es la operación inversa de {@link ArchiveProjectService}. Es idempotente:
 * un proyecto que no está archivado no necesita cambios, así que no se persiste.
 * En particular, esto evita reactivar por error un proyecto suspendido.</p>
 */
@Service
public class RestoreProjectService {

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RestoreProjectService(ProjectRepository projectRepository, ApplicationEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void restore(UUID projectId, UUID actorAccountId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
        // Idempotente: solo los proyectos archivados necesitan restaurarse (y así
        // se evita una escritura que solo actualizaría updatedAt, o reactivar un
        // proyecto suspendido).
        if (project.getStatus() != ProjectStatus.ARCHIVED) {
            return;
        }
        project.reactivate();
        projectRepository.save(project);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "project.restored", "project", projectId.toString(),
                actorAccountId, null));
    }
}
