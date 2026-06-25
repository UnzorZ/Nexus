package dev.unzor.nexus.admin.application.service;

import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Registra el instante del último inicio de sesión correcto de una cuenta.
 *
 * <p>La operación es <em>best-effort</em>: {@code lastLoginAt} es un dato
 * informativo, no una condición del inicio de sesión. La propia invocación se
 * envuelve en un {@code try/catch} y, de fallar, solo deja una advertencia en
 * el log; los llamadores (success handler del form-login y endpoint JSON de
 * login) no necesitan gestionar excepciones.</p>
 *
 * <p>No es transaccional a nivel de servicio: cada acceso al repositorio usa su
 * propia transacción (la de {@code SimpleJpaRepository}), de modo que un fallo
 * al guardar no deje ninguna transacción externa en un estado inconsistente.</p>
 */
@Service
public class RecordLoginService {

    private static final Logger log = LoggerFactory.getLogger(RecordLoginService.class);

    private final NexusAccountRepository accountRepository;

    public RecordLoginService(NexusAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public void recordLogin(UUID accountId) {
        try {
            accountRepository.findById(accountId).ifPresent(account -> {
                account.recordLogin(Instant.now());
                accountRepository.save(account);
            });
        } catch (RuntimeException exception) {
            log.warn("Failed to record last login for account {}", accountId, exception);
        }
    }
}
