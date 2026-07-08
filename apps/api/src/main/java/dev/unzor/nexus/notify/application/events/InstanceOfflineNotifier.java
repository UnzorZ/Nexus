package dev.unzor.nexus.notify.application.events;

import dev.unzor.nexus.notify.application.service.ProjectNotificationsService;
import dev.unzor.nexus.shared.audit.InstanceWentOffline;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Reacciona a {@link InstanceWentOffline} enviando un email al destinatario
 * configurado del proyecto. Se ejecuta asíncronamente en el {@code notifyExecutor}:
 * un SMTP lento no bloquea el barrido de registry que publicó el evento. El
 * {@code send} traga excepciones (SMTP caído → fila FAILED en {@code notifications}),
 * así que un fallo de envío no aborta nada. {@code actorAccountId=null} es el emisor
 * sistema (mismo valor que usa el runtime controller).
 */
@Component
public class InstanceOfflineNotifier {

    private final ProjectNotificationsService notificationsService;

    public InstanceOfflineNotifier(ProjectNotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @Async("notifyExecutor")
    @EventListener
    public void onInstanceWentOffline(InstanceWentOffline event) {
        String subject = "Instance offline: " + event.appName();
        String body = "<p>The instance <strong>" + escape(event.appName()) + "</strong>"
                + " (<code>" + escape(event.instanceId()) + "</code>) has not checked in"
                + " since " + event.lastSeenAt() + ".</p>"
                + "<p style=\"color:#6b7280;font-size:.85rem\">Project " + event.projectId() + "</p>";
        // Fan-out: un envío por destinatario. El send traga excepciones de SMTP
        // (fila FAILED en notifications), así que un fallo en uno no corta los demás.
        for (String recipient : event.recipients()) {
            notificationsService.send(
                    event.projectId(), recipient,
                    null, subject, body, null, null);
        }
    }

    /** Escape HTML mínimo de valores reportados por el cliente (anti inyección en el email). */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
