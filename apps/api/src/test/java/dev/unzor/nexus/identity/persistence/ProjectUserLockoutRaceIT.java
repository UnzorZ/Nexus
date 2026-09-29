package dev.unzor.nexus.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;

/**
 * Bloqueo de cuenta frente a intentos fallidos en paralelo (auditoría 2026-09-28, F20).
 *
 * <p>Modos de fallo escritos antes del código:
 * <ul>
 *   <li>Cada petición lee la entidad, suma 1 en memoria y guarda: las peticiones en paralelo
 *       pisan el contador y el bloqueo de 5 intentos no llega a aplicarse.</li>
 *   <li>Un bloqueo ya caducado debe reiniciar el contador (el usuario puede volver a entrar
 *       y volver a bloquearse).</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProjectUserLockoutRaceIT {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);

    @Autowired private ProjectUserRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void parallelFailuresAreAllCountedAndLockTheAccount() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        seed(projectId, userId, 0, null);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                Instant now = Instant.now();
                return repository.recordFailedLogin(projectId, userId, now, MAX_ATTEMPTS, now.plus(LOCK));
            }));
        }
        start.countDown();
        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(1);
        }
        pool.shutdown();

        Map<String, Object> row = row(userId);
        assertThat(((Number) row.get("failed_login_attempts")).intValue()).isEqualTo(threads);
        assertThat(row.get("locked_until")).isNotNull();
    }

    @Test
    void anExpiredLockRestartsTheCount() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        seed(projectId, userId, MAX_ATTEMPTS, Timestamp.from(Instant.now().minusSeconds(60)));

        Instant now = Instant.now();
        repository.recordFailedLogin(projectId, userId, now, MAX_ATTEMPTS, now.plus(LOCK));

        Map<String, Object> row = row(userId);
        assertThat(((Number) row.get("failed_login_attempts")).intValue()).isEqualTo(1);
        assertThat(row.get("locked_until")).isNull();
    }

    private Map<String, Object> row(UUID userId) {
        return jdbcTemplate.queryForMap(
                "SELECT failed_login_attempts, locked_until FROM project_users WHERE id = ?", userId);
    }

    private void seed(UUID projectId, UUID userId, int attempts, Timestamp lockedUntil) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                projectId, "lockout-" + projectId, "Lockout IT", now, now);
        jdbcTemplate.update(
                "INSERT INTO project_users (id, project_id, email, password_hash, display_name, status, authz_version, "
                        + "failed_login_attempts, locked_until, created_at, updated_at) VALUES (?,?,?,?,?,'ACTIVE',0,?,?,?,?)",
                userId, projectId, "u-" + userId + "@lockout.test", "noop", "U", attempts, lockedUntil, now, now);
    }
}
