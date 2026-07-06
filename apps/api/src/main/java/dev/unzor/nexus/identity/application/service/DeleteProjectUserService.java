package dev.unzor.nexus.identity.application.service;

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
 * Borrado definitivo de un usuario del proyecto. Existe el {@code disable()}
 * para una desactivación reversible; este servicio elimina la fila.
 */
@Service
public class DeleteProjectUserService {

    private final ProjectUserRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectUserSessionService sessions;

    public DeleteProjectUserService(
            ProjectUserRepository repository,
            ApplicationEventPublisher eventPublisher,
            ProjectUserSessionService sessions
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.sessions = sessions;
    }

    @Transactional
    public void delete(UUID projectId, UUID userId, UUID actorAccountId) {
        ProjectUser user = repository.findByProjectIdAndId(projectId, userId)
                .orElseThrow(() -> new ProjectUserNotFoundException(projectId, userId));
        UUID id = user.getId();
        String email = user.getEmail();
        repository.delete(user);
        // El usuario ya no existe: sus sesiones activas tampoco deben valer.
        sessions.revokeAll(id);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "project_user.deleted", "project_user", Objects.toString(id, null),
                actorAccountId, Map.of("email", email)));
    }
}
