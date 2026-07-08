package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.exception.SessionNotFoundException;
import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import dev.unzor.nexus.shared.security.SessionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestión de las sesiones {@code /p/**} de un usuario de proyecto (respaldadas en Redis e
 * indexadas por {@link NexusSessionAttributes#PROJECT_USER_ID}).
 *
 * <p>Espejo de {@code PanelSessionService} para el portal de usuario final: listado con
 * la sesión actual primero, revocación por identificador público y revocación total. Sólo
 * expone identificadores públicos ({@link NexusSessionAttributes#SESSION_PUBLIC_ID});
 * nunca el ID interno de Spring Session ni el valor de la cookie {@code JSESSIONID}.</p>
 *
 * <p>Inyecta el repositorio vía {@link ObjectProvider} (lazy): crearlo de forma eager
 * durante el refresco del contexto provoca que el {@code SessionRepositoryFilter} se
 * inicialice durante el arranque y rompe la inicialización de MockMvc en tests.</p>
 */
@Service
public class ProjectUserSessionService {

    private static final Logger log = LoggerFactory.getLogger(ProjectUserSessionService.class);

    private final ObjectProvider<RedisIndexedSessionRepository> sessionRepositoryProvider;

    public ProjectUserSessionService(ObjectProvider<RedisIndexedSessionRepository> sessionRepositoryProvider) {
        this.sessionRepositoryProvider = sessionRepositoryProvider;
    }

    /**
     * Devuelve las sesiones del usuario, ordenadas con la sesión actual primero (si se
     * indica) y después por último acceso descendente. Lista vacía si Redis no está
     * disponible.
     */
    public List<SessionSummary> listForUser(UUID userId, UUID currentSessionPublicId) {
        RedisIndexedSessionRepository sessionRepository = sessionRepositoryProvider.getIfAvailable();
        if (sessionRepository == null) {
            return List.of();
        }
        return sessionsByUser(sessionRepository, userId).values().stream()
                .map(session -> toSummary(session, currentSessionPublicId))
                .sorted(currentFirstThenByLastAccessedDescending(currentSessionPublicId))
                .toList();
    }

    /**
     * Revoca la sesión con el identificador público indicado, si pertenece al usuario.
     *
     * @throws SessionNotFoundException si no existe, no pertenece al usuario o Redis no
     *                                  está disponible (no revela cuál)
     */
    public void revokeByPublicId(UUID userId, UUID publicSessionId) {
        RedisIndexedSessionRepository sessionRepository = sessionRepositoryProvider.getIfAvailable();
        if (sessionRepository == null) {
            throw new SessionNotFoundException(publicSessionId.toString());
        }
        Session target = sessionsByUser(sessionRepository, userId).values().stream()
                .filter(session -> matchesPublicId(session, publicSessionId))
                .findFirst()
                .orElseThrow(() -> new SessionNotFoundException(publicSessionId.toString()));
        sessionRepository.deleteById(target.getId());
    }

    /**
     * Revoca todas las sesiones del usuario. Pensado para invocarse al
     * suspender/desactivar/borrar un usuario, de modo que una sesión activa con un
     * {@code ProjectUserPrincipal} stale deje de valer de inmediato.
     *
     * <p>Idempotente: borrar sesiones que ya no existen no lanza.</p>
     */
    public void revokeAll(UUID userId) {
        RedisIndexedSessionRepository sessionRepository = sessionRepositoryProvider.getIfAvailable();
        if (sessionRepository == null) {
            return;
        }
        try {
            sessionRepository.findByPrincipalName(NexusSessionAttributes.projectUserIndexValue(userId))
                    .keySet().forEach(sessionRepository::deleteById);
        } catch (RuntimeException e) {
            log.warn("Failed to revoke project-user sessions: userId={}", userId, e);
        }
    }

    private static Map<String, RedisSession> sessionsByUser(
            RedisIndexedSessionRepository sessionRepository, UUID userId) {
        // Indexadas bajo el índice PRINCIPAL_NAME estándar con el valor
        // "project-user:<userId>" (ver PanelSessionConfiguration#nexusAccountIdIndexResolver).
        return sessionRepository.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                NexusSessionAttributes.projectUserIndexValue(userId));
    }

    private static boolean matchesPublicId(Session session, UUID publicSessionId) {
        String value = session.getAttribute(NexusSessionAttributes.SESSION_PUBLIC_ID);
        return value != null && value.equals(publicSessionId.toString());
    }

    private static SessionSummary toSummary(Session session, UUID currentSessionPublicId) {
        String publicIdValue = session.getAttribute(NexusSessionAttributes.SESSION_PUBLIC_ID);
        UUID publicId = publicIdValue != null ? UUID.fromString(publicIdValue) : null;
        int maxInactive = session.getMaxInactiveInterval() != null
                ? (int) session.getMaxInactiveInterval().toSeconds()
                : 0;
        Instant createdAt = session.getCreationTime();
        Instant lastAccessedAt = session.getLastAccessedTime();
        Instant expiresAt = maxInactive > 0
                ? lastAccessedAt.plus(Duration.ofSeconds(maxInactive))
                : null;

        return new SessionSummary(
                publicId,
                currentSessionPublicId != null && currentSessionPublicId.equals(publicId),
                session.getAttribute(NexusSessionAttributes.USER_AGENT),
                createdAt,
                lastAccessedAt,
                expiresAt,
                maxInactive
        );
    }

    private static Comparator<SessionSummary> currentFirstThenByLastAccessedDescending(UUID currentSessionPublicId) {
        return Comparator
                .comparing((SessionSummary summary) -> !(currentSessionPublicId != null
                        && currentSessionPublicId.toString().equals(summary.id() == null ? null : summary.id().toString())))
                .thenComparing(SessionSummary::lastAccessedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }
}
