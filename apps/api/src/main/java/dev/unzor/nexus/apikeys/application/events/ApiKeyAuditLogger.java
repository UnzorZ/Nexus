package dev.unzor.nexus.apikeys.application.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Registra los eventos de auditoría de API keys en log (observabilidad). La
 * persistencia en {@code audit_events} llega con el milestone de Audit; este
 * listener garantiza que los eventos ya son observables mientras tanto.
 *
 * <p>Usa {@code AFTER_COMMIT} con {@code fallbackExecution}: las mutations del
 * servicio (transaccionales) solo se auditan si la transacción confirma (sin
 * falsos éxitos tras rollback); los rechazos del filtro (sin transacción
 * activa) se disparan inmediatamente por el fallback.</p>
 */
@Component
class ApiKeyAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("dev.unzor.nexus.audit.ApiKey");

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onApiKeyAudit(ApiKeyAuditEvent event) {
        log.info(
                "action={} project={} key={} actor={}:{} trace={} reason={} metadata={}",
                event.action(),
                event.projectId(),
                event.keyId(),
                event.actorType(),
                event.actorId(),
                event.traceId(),
                event.reason(),
                event.metadata());
    }
}
