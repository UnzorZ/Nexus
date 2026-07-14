package dev.unzor.nexus.metrics.application.events;

import dev.unzor.nexus.metrics.application.configuration.MetricsRetentionProperties;
import dev.unzor.nexus.metrics.application.service.ProjectMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Purga periódica de métricas: borra los puntos más antiguos que
 * {@link MetricsRetentionProperties#retentionDays()} para que la tabla
 * {@code project_metrics} (append-only, alta frecuencia) no crezca sin límite.
 * Global (todos los proyectos); un operador puede desactivarla
 * ({@code nexus.metrics.retention.enabled=false}) o afinar la ventana. El borrado
 * real lo hace {@link ProjectMetricsService#purgeOlderThan}.
 */
@Component
public class MetricsRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(MetricsRetentionJob.class);

    private final ProjectMetricsService service;
    private final MetricsRetentionProperties properties;

    public MetricsRetentionJob(ProjectMetricsService service, MetricsRetentionProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(cron = "${nexus.metrics.retention.cron:0 0 4 * * *}")
    public void purgeOldPoints() {
        if (!properties.enabled() || properties.retentionDays() <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(properties.retentionDays(), ChronoUnit.DAYS);
        long deleted = service.purgeOlderThan(cutoff);
        log.info("metrics retention: removed {} point(s) older than {} day(s) (cutoff {})",
                deleted, properties.retentionDays(), cutoff);
    }
}
