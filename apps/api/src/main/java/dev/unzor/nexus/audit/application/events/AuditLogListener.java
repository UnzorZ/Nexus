package dev.unzor.nexus.audit.application.events;

import dev.unzor.nexus.audit.domain.entity.AuditLogEntry;
import dev.unzor.nexus.audit.persistence.repository.AuditLogRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sumidero único de {@link AuditEvent}: persiste cada evento en {@code audit_log}
 * y deja una línea de log estructurada.
 * <p>
 * Síncrono ({@link EventListener}, no {@code @TransactionalEventListener}): en la
 * vía de panel (servicio transaccional) la escritura participa de la misma
 * transacción que la operación auditada —si ésta hace rollback, no se audita un
 * éxito falso—; en la vía de rechazo de auth (sin transacción) el
 * {@link TransactionTemplate} abre una transacción propia. Best-effort: un fallo
 * de persistencia se loguea y se traga para no romper la operación de negocio.
 * Reemplaza al antiguo {@code ApiKeyAuditLogger} (que solo logueaba).
 */
@Component
class AuditLogListener {

    private static final Logger log = LoggerFactory.getLogger("dev.unzor.nexus.audit.AuditLog");

    private final AuditLogRepository repository;
    private final TransactionTemplate transactionTemplate;

    AuditLogListener(AuditLogRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @EventListener
    void onAuditEvent(AuditEvent event) {
        log.info("action={} project={} resource={}:{} severity={} actor={}:{} trace={} ip={}",
                event.action(), event.projectId(), event.resourceType(), event.resourceId(),
                event.severity(), event.actorType(), event.actorId(), event.traceId(), event.ip());
        try {
            transactionTemplate.executeWithoutResult(status -> repository.save(AuditLogEntry.from(event)));
        } catch (RuntimeException e) {
            log.warn("No se pudo persistir el evento de auditoría '{}': {}", event.action(), e.toString());
        }
    }
}
