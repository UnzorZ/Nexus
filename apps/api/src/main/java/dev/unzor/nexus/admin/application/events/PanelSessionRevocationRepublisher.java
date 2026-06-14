package dev.unzor.nexus.admin.application.events;

import dev.unzor.nexus.admin.domain.events.NexusAccountSessionsRevocationRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Garantiza la entrega fiable de la revocación de sesiones desencadenada por cambios de
 * estado de la cuenta (suspensión, desactivación, retirada de {@code instanceAdmin}).
 *
 * <p>Spring Modulith persiste las publicaciones de eventos en PostgreSQL. Si Redis
 * falla justo después de que la transacción que muta la cuenta hace commit, el listener
 * de revocación lanza una excepción y la publicación queda incompleta. Este componente
 * reentrega esas publicaciones:</p>
 *
 * <ul>
 *   <li>al arrancar la aplicación, una vez, reentregando <em>inmediatamente</em> las
 *       revocaciones pendientes ({@code minAge = Duration.ZERO}), y</li>
 *   <li>periódicamente de forma acotada, reentregando las publicaciones de revocación
 *       más antiguas que {@link PanelSessionRevocationProperties#resubmitMinAge()}.</li>
 * </ul>
 *
 * <p>Solo se reentregan publicaciones cuyo evento sea
 * {@link NexusAccountSessionsRevocationRequested}; otros tipos de evento no se ven
 * afectados. La operación de revocación
 * ({@code PanelSessionService.revokeAllForAccount(accountId)}) es idempotente, por lo que
 * la reentrega es segura incluso si una revocación previa ya había surtido efecto.</p>
 */
@Component
@EnableConfigurationProperties(PanelSessionRevocationProperties.class)
class PanelSessionRevocationRepublisher {

    private static final Logger log = LoggerFactory.getLogger(PanelSessionRevocationRepublisher.class);

    private final IncompleteEventPublications incompletePublications;
    private final PanelSessionRevocationProperties properties;

    PanelSessionRevocationRepublisher(
            IncompleteEventPublications incompletePublications,
            PanelSessionRevocationProperties properties) {
        this.incompletePublications = incompletePublications;
        this.properties = properties;
    }

    /**
     * Reentrega las revocaciones pendientes al arrancar la aplicación. Usa
     * {@code minAge = Duration.ZERO} para reentregarlas inmediatamente, cubriendo el caso
     * en que un reinicio ocurre con revocaciones pendientes por un fallo previo de Redis.
     */
    @EventListener(ApplicationReadyEvent.class)
    void resubmitOnStartup() {
        resubmit(Duration.ZERO, "startup");
    }

    /**
     * Barrido periódico acotado: reentrega las publicaciones de revocación más antiguas
     * que {@link PanelSessionRevocationProperties#resubmitMinAge()}. La cadencia por
     * defecto es de 60 segundos.
     */
    @Scheduled(fixedDelayString = "${nexus.session.revocation.resubmit-interval:60s}")
    void resubmitPeriodically() {
        resubmit(properties.resubmitMinAge(), "scheduled");
    }

    private void resubmit(Duration minAge, String trigger) {
        ResubmissionOptions options = options(minAge);
        try {
            incompletePublications.resubmitIncompletePublications(options);
        } catch (RuntimeException exception) {
            // La reentrega nunca debe tirar la aplicación; se reintenta en el siguiente ciclo.
            log.warn("Session revocation resubmission ({}) failed and will be retried", trigger, exception);
        }
    }

    /**
     * Construye las opciones de reentrega: filtra por
     * {@link NexusAccountSessionsRevocationRequested} y aplica los límites configurados
     * de tamaño de lote, máximo en vuelo y edad mínima.
     */
    ResubmissionOptions options(Duration minAge) {
        return ResubmissionOptions.defaults()
                .withFilter(PanelSessionRevocationRepublisher::isRevocationEvent)
                .withBatchSize(properties.resubmitBatchSize())
                .withMaxInFlight(properties.resubmitMaxInFlight())
                .withMinAge(minAge);
    }

    private static boolean isRevocationEvent(EventPublication publication) {
        return publication.getEvent() instanceof NexusAccountSessionsRevocationRequested;
    }
}
