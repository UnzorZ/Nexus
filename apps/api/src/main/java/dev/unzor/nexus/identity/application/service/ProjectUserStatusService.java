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
    private final ProjectUserOAuthRevocationService oauthRevocation;

    public ProjectUserStatusService(
            ProjectUserRepository repository,
            ApplicationEventPublisher eventPublisher,
            ProjectUserSessionService sessions,
            ProjectUserOAuthRevocationService oauthRevocation
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.sessions = sessions;
        this.oauthRevocation = oauthRevocation;
    }

    @Transactional
    public ProjectUserDetails suspend(UUID projectId, UUID userId, UUID actorAccountId) {
        return restrict(projectId, userId, actorAccountId, "project_user.suspended", ProjectUser::suspend);
    }

    @Transactional
    public ProjectUserDetails reactivate(UUID projectId, UUID userId, UUID actorAccountId) {
        return transition(projectId, userId, actorAccountId, "project_user.reactivated", ProjectUser::reactivate);
    }

    @Transactional
    public ProjectUserDetails disable(UUID projectId, UUID userId, UUID actorAccountId) {
        return restrict(projectId, userId, actorAccountId, "project_user.disabled", ProjectUser::disable);
    }

    /**
     * Suspender/desactivar (remediación de auditoría #4): cambia el estado, bump de
     * {@code authz_version} (introspection → inactivo; los JWT locales caducan solos) y
     * revoca sesiones HTTP + autorizaciones OAuth persistidas. Sin esto último, un
     * refresh token podía seguir emitiendo tokens nuevos tras suspender/desactivar.
     */
    private ProjectUserDetails restrict(
            UUID projectId, UUID userId, UUID actorAccountId, String action, Consumer<ProjectUser> mutator
    ) {
        ProjectUser user = load(projectId, userId);
        mutator.accept(user);
        user.incrementAuthzVersion();
        ProjectUser saved = repository.save(user);
        revokeCredentials(projectId, userId, saved);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, action, "project_user", Objects.toString(saved.getId(), null),
                actorAccountId, Map.of()));
        return ProjectUserDetails.from(saved);
    }

    private void revokeCredentials(UUID projectId, UUID userId, ProjectUser user) {
        oauthRevocation.revokeForProjectUser(projectId, userId);
        sessions.revokeAll(userId);
    }

    /**
     * Revoca el acceso del usuario sin cambiar su estado: bump de {@code authz_version}
     * (los resource servers que validan por introspection verán el token como
     * inactivo) + revoca las sesiones activas (panel y realm del proyecto). El
     * usuario debe reautenticarse en todas partes. No suspende ni desactiva.
     */
    @Transactional
    public ProjectUserDetails revokeTokens(UUID projectId, UUID userId, UUID actorAccountId) {
        ProjectUser user = load(projectId, userId);
        user.incrementAuthzVersion();
        ProjectUserDetails details = ProjectUserDetails.from(repository.save(user));
        // Sesiones HTTP + autorizaciones OAuth persistidas (refresh tokens).
        revokeCredentials(projectId, userId, user);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "project_user.tokens_revoked", "project_user",
                Objects.toString(user.getId(), null), actorAccountId, Map.of()));
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
