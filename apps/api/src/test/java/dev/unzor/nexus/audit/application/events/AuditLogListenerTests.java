package dev.unzor.nexus.audit.application.events;

import dev.unzor.nexus.audit.domain.entity.AuditLogEntry;
import dev.unzor.nexus.audit.persistence.repository.AuditLogRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.audit.AuditOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogListenerTests {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogListener listener = new AuditLogListener(repository, noopTransactionManager());

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void byAccountFactoryFillsContextFromMdcAndListenerMapsEveryField() {
        MDC.put("ip", "203.0.113.9");
        MDC.put("userAgent", "Mozilla/5.0");
        MDC.put("traceId", "trace-abc");
        UUID projectId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        AuditEvent event = AuditEvent.byAccount(
                projectId, "api_key.created", "api_key", "key-1",
                AuditOutcome.SUCCESS, actor, Map.of("name", "CI"));

        listener.onAuditEvent(event);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository).save(captor.capture());
        AuditLogEntry saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(projectId);
        assertThat(saved.getAction()).isEqualTo("api_key.created");
        assertThat(saved.getResourceType()).isEqualTo("api_key");
        assertThat(saved.getResourceId()).isEqualTo("key-1");
        assertThat(saved.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(saved.getActorType()).isEqualTo("NEXUS_ACCOUNT");
        assertThat(saved.getActorId()).isEqualTo(actor.toString());
        assertThat(saved.getIp()).isEqualTo("203.0.113.9");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getTraceId()).isEqualTo("trace-abc");
        assertThat(saved.getMetadata()).containsEntry("name", "CI");
        // occurredAt lo fija @PrePersist al persistir, no en el mapeo.
        assertThat(saved.getOccurredAt()).isNull();
    }

    @Test
    void anonymousFactoryCarriesNoActorAndPersists() {
        AuditEvent event = AuditEvent.anonymous(
                null, "api_key.auth_invalid", "api_key", null, AuditOutcome.FAILURE, "no_match");

        assertThat(event.actorType()).isEqualTo("ANONYMOUS");
        assertThat(event.actorId()).isNull();
        assertThat(event.outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(event.metadata()).containsEntry("reason", "no_match");

        listener.onAuditEvent(event);

        verify(repository).save(any(AuditLogEntry.class));
    }

    /** Gestor sin efecto real para que el {@code TransactionTemplate} del listener
     * ejecute el callback de forma síncrona en los tests de unidad. */
    private static PlatformTransactionManager noopTransactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected boolean isExistingTransaction(Object transaction) {
                return false;
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                // no-op
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                // no-op
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                // no-op
            }
        };
    }
}
