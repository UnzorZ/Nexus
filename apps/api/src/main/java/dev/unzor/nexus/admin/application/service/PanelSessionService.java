package dev.unzor.nexus.admin.application.service;

import dev.unzor.nexus.admin.api.dto.SessionSummary;
import dev.unzor.nexus.admin.application.configuration.PanelSessionConfiguration;
import dev.unzor.nexus.admin.domain.exception.SessionNotFoundException;
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
 * Servicio de aplicación para la gestión de sesiones del panel respaldadas por Redis.
 *
 * <p>Delega en {@link RedisIndexedSessionRepository}, que indexa las sesiones por el
 * identificador de la cuenta (ver
 * {@link PanelSessionConfiguration#nexusAccountIdIndexResolver()}). Solo expone
 * identificadores públicos ({@code nexus.sessionPublicId}); nunca el ID interno de
 * Spring Session ni el valor de la cookie {@code JSESSIONID}.</p>
 *
 * <p>Una cuenta solo puede consultar o revocar sus propias sesiones; una sesión ajena o
 * inexistente se trata como no encontrada ({@link SessionNotFoundException}).</p>
 */
@Service
public class PanelSessionService {

    private final RedisIndexedSessionRepository sessionRepository;

    public PanelSessionService(RedisIndexedSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Devuelve las sesiones de la cuenta, ordenadas con la sesión actual primero (si se
     * indica) y después por último acceso descendente.
     */
    public List<SessionSummary> listForAccount(UUID accountId, UUID currentSessionPublicId) {
        return sessionsByAccount(accountId).values().stream()
                .map(session -> toSummary(session, currentSessionPublicId))
                .sorted(currentFirstThenByLastAccessedDescending(currentSessionPublicId))
                .toList();
    }

    /**
     * Devuelve las sesiones de la cuenta sin marcar ninguna como actual.
     */
    public List<SessionSummary> listForAccount(UUID accountId) {
        return listForAccount(accountId, null);
    }

    /**
     * Revoca la sesión con el identificador público indicado, si pertenece a la cuenta.
     *
     * @throws SessionNotFoundException si no existe o no pertenece a la cuenta
     */
    public void revokeByPublicId(UUID accountId, UUID publicSessionId) {
        Map<String, RedisSession> sessions = sessionsByAccount(accountId);
        Session target = sessions.values().stream()
                .filter(session -> matchesPublicId(session, publicSessionId))
                .findFirst()
                .orElseThrow(() -> new SessionNotFoundException(publicSessionId.toString()));
        sessionRepository.deleteById(target.getId());
    }

    /**
     * Revoca todas las sesiones de la cuenta. Pensado para usarse desde la propia cuenta
     * (gestión de sesiones) y como punto único para futuros disparadores de revocación:
     * cambio de contraseña, suspensión, desactivación o retirada de
     * {@code instanceAdmin}.
     *
     * <p>La operación es <strong>idempotente</strong>: borrar sesiones que ya no existen
     * no lanza, por lo que una reentrega del evento de revocación (p. ej. tras un fallo
     * temporal de Redis) es segura.</p>
     */
    public void revokeAllForAccount(UUID accountId) {
        sessionsByAccount(accountId).keySet()
                .forEach(sessionRepository::deleteById);
    }

    private Map<String, RedisSession> sessionsByAccount(UUID accountId) {
        // Sessions are indexed under the standard PRINCIPAL_NAME index with the account
        // id as value (see PanelSessionConfiguration#nexusAccountIdIndexResolver). This is
        // the only index RedisIndexedSessionRepository consults in findByIndexNameAndIndexValue.
        return sessionRepository.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                accountId.toString()
        );
    }

    private static boolean matchesPublicId(Session session, UUID publicSessionId) {
        String value = session.getAttribute(PanelSessionConfiguration.SESSION_PUBLIC_ID);
        return value != null && value.equals(publicSessionId.toString());
    }

    private static SessionSummary toSummary(Session session, UUID currentSessionPublicId) {
        String publicIdValue = session.getAttribute(PanelSessionConfiguration.SESSION_PUBLIC_ID);
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
                session.getAttribute(PanelSessionConfiguration.USER_AGENT),
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
