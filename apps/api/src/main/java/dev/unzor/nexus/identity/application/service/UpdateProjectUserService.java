package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.ProjectUserDetails;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.exception.ProjectUserNotFoundException;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Actualización del perfil de un usuario (displayName + username). No toca
 * estado ni contraseña.
 */
@Service
public class UpdateProjectUserService {

    private final ProjectUserRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateProjectUserService(ProjectUserRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProjectUserDetails update(
            UUID projectId, UUID userId, String displayName, String username, UUID actorAccountId
    ) {
        ProjectUser user = load(projectId, userId);
        user.updateProfile(displayName.trim(), username == null || username.isBlank() ? null : username.trim());
        ProjectUser saved = repository.save(user);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "project_user.updated", "project_user", Objects.toString(saved.getId(), null),
                actorAccountId, Map.of()));
        return ProjectUserDetails.from(saved);
    }

    private ProjectUser load(UUID projectId, UUID userId) {
        return repository.findByProjectIdAndId(projectId, userId)
                .orElseThrow(() -> new ProjectUserNotFoundException(projectId, userId));
    }
}
