package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.audit.AuditOutcome;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso para archivar un proyecto de forma reversible (estado ARCHIVED).
 */
@Service
public class ArchiveProjectService {

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ArchiveProjectService(ProjectRepository projectRepository, ApplicationEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void archive(UUID projectId, UUID actorAccountId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
        // Idempotent: an already-archived project has nothing to do (and avoids a
        // pointless write that would only bump updatedAt).
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            return;
        }
        project.archive();
        projectRepository.save(project);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "project.archived", "project", projectId.toString(),
                AuditOutcome.SUCCESS, actorAccountId, null));
    }
}
