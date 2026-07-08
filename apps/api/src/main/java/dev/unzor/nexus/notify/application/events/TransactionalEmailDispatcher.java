package dev.unzor.nexus.notify.application.events;

import dev.unzor.nexus.notify.application.service.ProjectNotificationsService;
import dev.unzor.nexus.shared.audit.OutboundTransactionalEmail;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Entrega los emails transaccionales (verificación de email, reseteo de contraseña,
 * MFA) publicados por otros módulos (p.ej. {@code identity}).
 *
 * <p>Espejo de {@link InstanceOfflineNotifier}: el emisor ya construye el cuerpo HTML
 * (conoce el token y el enlace); aquí sólo se delega en
 * {@link ProjectNotificationsService#send}, que envía por SMTP y persiste la fila
 * {@code notifications} + auditoría. Se ejecuta asíncronamente en el
 * {@code notifyExecutor} para que un SMTP lento no bloquee la petición que publicó el
 * email. {@code actorAccountId=null} = emisor sistema.
 * El {@code send} traga excepciones de SMTP (fila FAILED), así que un fallo de envío
 * no aborta al emisor.</p>
 */
@Component
public class TransactionalEmailDispatcher {

    private final ProjectNotificationsService notificationsService;

    public TransactionalEmailDispatcher(ProjectNotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @Async("notifyExecutor")
    @EventListener
    public void onOutboundEmail(OutboundTransactionalEmail event) {
        notificationsService.send(
                event.projectId(), event.recipientEmail(),
                null, event.subject(), event.htmlBody(), null, null);
    }
}
