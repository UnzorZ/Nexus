package dev.unzor.nexus.apikeys.application.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registra los eventos de auditoría de API keys en log (observabilidad). La
 * persistencia en {@code audit_events} llega con el milestone de Audit; este
 * listener garantiza que los eventos ya son observables mientras tanto.
 */
@Component
class ApiKeyAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("dev.unzor.nexus.audit.ApiKey");

    @EventListener
    void onApiKeyAudit(ApiKeyAuditEvent event) {
        log.info(
                "action={} project={} key={} actor={}:{} reason={} metadata={}",
                event.action(),
                event.projectId(),
                event.keyId(),
                event.actorType(),
                event.actorId(),
                event.reason(),
                event.metadata());
    }
}
