package dev.unzor.nexus.audit.application.service;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.audit.domain.entity.AuditLogEntry;
import dev.unzor.nexus.audit.persistence.repository.AuditLogRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validación runtime de la retención + el export NDJSON del módulo audit. Se
 * prueba a nivel de servicio (sin HTTP/auth): se siembran dos entradas, una
 * backdateada vía SQL nativo (occurred_at es updatable=false), se purga por
 * cutoff y se exporta el resto.
 *
 * <p>El seeding se envuelve en una transacción explícita ({@link TransactionTemplate})
 * porque {@code AuditLogRepository.save} no abre su propia tx (ver
 * {@code AuditLogListener}); tras el flush, el UPDATE nativo backdatea la fila
 * antes del commit.</p>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditRetentionAndExportIT {

    @Autowired
    private AuditQueryService service;

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID projectId = UUID.randomUUID();

    @Test
    void purgeRemovesOnlyStaleEntriesAndExportStreamsTheRest() throws Exception {
        UUID staleId = seedAndBackdate();

        long deleted = service.purgeOlderThan(Instant.now().minus(365, ChronoUnit.DAYS));
        assertThat(deleted).isGreaterThanOrEqualTo(1L); // al menos la fila stale (400d)

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportForProject(projectId, null, out);
        String ndjson = out.toString(StandardCharsets.UTF_8);

        assertThat(ndjson).contains("retain.action");   // la reciente sobrevive
        assertThat(ndjson).doesNotContain("purge.action"); // la stale fue purgada
        assertThat(ndjson.lines().count()).isGreaterThanOrEqualTo(1L);
    }

    /** Siembra una entrada reciente y una stale (occurred_at = ahora - 400d) en una tx. */
    private UUID seedAndBackdate() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Instant ancient = Instant.now().minus(400, ChronoUnit.DAYS);
        return tx.execute(status -> {
            AuditLogEntry recent = repository.save(entry("retain.action"));
            AuditLogEntry stale = repository.save(entry("purge.action"));
            entityManager.flush(); // asegura el INSERT antes del UPDATE nativo
            entityManager.createNativeQuery("UPDATE audit_log SET occurred_at = :when WHERE id = :id")
                    .setParameter("when", ancient)
                    .setParameter("id", stale.getId())
                    .executeUpdate();
            return stale.getId();
        });
    }

    private AuditLogEntry entry(String action) {
        return AuditLogEntry.from(
                AuditEvent.anonymous(projectId, action, "test_resource", "1", "reason"));
    }
}
