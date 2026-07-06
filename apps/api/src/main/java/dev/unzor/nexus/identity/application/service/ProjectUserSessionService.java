package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Revoca las sesiones {@code /p/**} de un usuario de proyecto (respaldadas en Redis e
 * indexadas por {@link NexusSessionAttributes#PROJECT_USER_ID}). Pensado para
 * invocarse al suspender/desactivar/borrar un usuario, de modo que una sesión activa
 * con un {@code ProjectUserPrincipal} stale deje de valer de inmediato.
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
}
