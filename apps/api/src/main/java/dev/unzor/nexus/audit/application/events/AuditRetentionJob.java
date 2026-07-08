package dev.unzor.nexus.audit.application.events;

import dev.unzor.nexus.audit.application.configuration.AuditRetentionProperties;
import dev.unzor.nexus.audit.application.service.AuditQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Purga periódica del log de auditoría: borra las entradas más antiguas que
 * {@link AuditRetentionProperties#retentionDays()} para que la tabla
 * {@code audit_log} no crezca sin límite. Global (todas las entradas); un
 * operador puede desactivarla ({@code nexus.audit.retention.enabled=false}) o
 * afinar la ventana. El borrado real lo hace {@link AuditQueryService#purgeOlderThan}.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditQueryService service;
    private final AuditRetentionProperties properties;

    public AuditRetentionJob(AuditQueryService service, AuditRetentionProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(cron = "${nexus.audit.retention.cron:0 30 3 * * *}")
    public void purgeOldEntries() {
        if (!properties.enabled() || properties.retentionDays() <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(properties.retentionDays(), ChronoUnit.DAYS);
        long deleted = service.purgeOlderThan(cutoff);
        log.info("audit retention: removed {} entr(y/ies) older than {} day(s) (cutoff {})",
                deleted, properties.retentionDays(), cutoff);
    }
}
