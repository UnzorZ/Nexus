package dev.unzor.nexus.metrics.application.service;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.metrics.domain.entity.ProjectMetric;
import dev.unzor.nexus.metrics.persistence.repository.ProjectMetricRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validación runtime de la retención del módulo metrics (paridad con
 * {@code AuditRetentionAndExportIT}). Se prueba a nivel de servicio (sin HTTP/auth):
 * se siembran dos puntos —uno reciente y uno backdateado a 400d— y se purga con un
 * cutoff de 365d; sólo el punto stale debe desaparecer.
 *
 * <p>A diferencia de audit, {@link ProjectMetric} toma {@code recordedAt} en el
 * constructor (lo fija el caller, no la entidad), así que el backdateo no necesita
 * UPDATE nativo. El seeding se envuelve en una transacción explícita
 * ({@link TransactionTemplate}) porque {@code ProjectMetricRepository.save} no abre
 * la suya.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MetricsRetentionIT {

    @Autowired
    private ProjectMetricsService service;

    @Autowired
    private ProjectMetricRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID projectId = UUID.randomUUID();

    @Test
    void purgeRemovesOnlyStalePoints() {
        // project_metrics.project_id tiene FK a projects(id): sembramos el proyecto.
        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Metrics IT', 'ACTIVE', now(), now()) ON CONFLICT (id) DO NOTHING",
                projectId, "metrics-it-" + projectId);

        UUID[] ids = seedRecentAndStale();
        UUID recentId = ids[0];
        UUID staleId = ids[1];

        long deleted = service.purgeOlderThan(Instant.now().minus(365, ChronoUnit.DAYS));
        assertThat(deleted).isGreaterThanOrEqualTo(1L); // al menos el punto stale (400d)

        assertThat(countById(staleId)).isZero();        // el stale fue purgado
        assertThat(countById(recentId)).isEqualTo(1L);  // el reciente sobrevive
    }

    /** Siembra un punto reciente y uno stale (recorded_at = ahora - 400d) en una tx. */
    private UUID[] seedRecentAndStale() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Instant ancient = Instant.now().minus(400, ChronoUnit.DAYS);
        return tx.execute(status -> {
            ProjectMetric recent = repository.save(
                    new ProjectMetric(projectId, "retain.metric", 1.0, Map.of(), Instant.now()));
            ProjectMetric stale = repository.save(
                    new ProjectMetric(projectId, "purge.metric", 2.0, Map.of(), ancient));
            return new UUID[]{recent.getId(), stale.getId()};
        });
    }

    private long countById(UUID id) {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM project_metrics WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult()).longValue();
    }
}
