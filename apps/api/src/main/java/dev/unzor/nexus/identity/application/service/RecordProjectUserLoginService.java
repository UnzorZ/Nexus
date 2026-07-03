package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Registra el instante del último login correcto de un usuario de proyecto.
 * Best-effort (no transaccional, traga errores): un fallo aquí no debe romper
 * el login. Espejea el {@code RecordLoginService} del panel.
 */
@Service
public class RecordProjectUserLoginService {

    private static final Logger log = LoggerFactory.getLogger(RecordProjectUserLoginService.class);

    private final ProjectUserRepository repository;

    public RecordProjectUserLoginService(ProjectUserRepository repository) {
        this.repository = repository;
    }

    public void recordLogin(UUID projectId, UUID userId) {
        try {
            repository.findByProjectIdAndId(projectId, userId).ifPresent(user -> {
                user.recordLogin(Instant.now());
                repository.save(user);
            });
        } catch (RuntimeException e) {
            log.warn("Failed to record project-user login: projectId={}, userId={}", projectId, userId, e);
        }
    }
}
