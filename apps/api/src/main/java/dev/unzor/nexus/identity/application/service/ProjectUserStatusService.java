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
import java.util.function.Consumer;

/**
 * Transiciones de estado de un usuario (suspender / reactivar / desactivar).
 * Las tres comparten la misma forma (cargar → mutar → guardar → auditar) y
 * delegan en los métodos de dominio del {@link ProjectUser}.
 */
@Service
public class ProjectUserStatusService {

    private final ProjectUserRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectUserSessionService sessions;

    public ProjectUserStatusService(
            ProjectUserRepository repository,
            ApplicationEventPublisher eventPublisher,
            ProjectUserSessionService sessions
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.sessions = sessions;
    }

    @Transactional
    public ProjectUserDetails suspend(UUID projectId, UUID userId, UUID actorAccountId) {
        ProjectUserDetails details = transition(projectId, userId, actorAccountId, "project_user.suspended", ProjectUser::suspend);
        // Revoca las sesiones activas: un ProjectUserPrincipal stale no debe seguir
        // teniendo acceso (web ni OAuth) hasta que la sesión expirase.
        sessions.revokeAll(userId);
        return details;
    }

    @Transactional
    public ProjectUserDetails reactivate(UUID projectId, UUID userId, UUID actorAccountId) {
        return transition(projectId, userId, actorAccountId, "project_user.reactivated", ProjectUser::reactivate);
    }

    @Transactional
    public ProjectUserDetails disable(UUID projectId, UUID userId, UUID actorAccountId) {
        ProjectUserDetails details = transition(projectId, userId, actorAccountId, "project_user.disabled", ProjectUser::disable);
        sessions.revokeAll(userId);
        return details;
    }

    private ProjectUserDetails transition(
            UUID projectId, UUID userId, UUID actorAccountId, String action, Consumer<ProjectUser> mutator
    ) {
        ProjectUser user = load(projectId, userId);
        mutator.accept(user);
        ProjectUser saved = repository.save(user);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, action, "project_user", Objects.toString(saved.getId(), null),
                actorAccountId, Map.of()));
        return ProjectUserDetails.from(saved);
    }

    private ProjectUser load(UUID projectId, UUID userId) {
        return repository.findByProjectIdAndId(projectId, userId)
                .orElseThrow(() -> new ProjectUserNotFoundException(projectId, userId));
    }
}
