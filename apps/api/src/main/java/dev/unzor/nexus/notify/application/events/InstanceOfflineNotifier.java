package dev.unzor.nexus.notify.application.events;

import dev.unzor.nexus.notify.application.service.ProjectNotificationsService;
import dev.unzor.nexus.shared.audit.InstanceWentOffline;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacciona a {@link InstanceWentOffline} enviando un email al destinatario
 * configurado del proyecto. Listener síncrono dentro del barrido de registry; el
 * {@code send} traga excepciones (SMTP caído → fila FAILED en {@code notifications}),
 * así que no aborta el barrido. {@code actorAccountId=null} es el emisor sistema
 * (mismo valor que usa el runtime controller).
 */
@Component
public class InstanceOfflineNotifier {

    private final ProjectNotificationsService notificationsService;

    public InstanceOfflineNotifier(ProjectNotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @EventListener
    public void onInstanceWentOffline(InstanceWentOffline event) {
        String subject = "Instance offline: " + event.appName();
        String body = "<p>The instance <strong>" + escape(event.appName()) + "</strong>"
                + " (<code>" + escape(event.instanceId()) + "</code>) has not checked in"
                + " since " + event.lastSeenAt() + ".</p>"
                + "<p style=\"color:#6b7280;font-size:.85rem\">Project " + event.projectId() + "</p>";
        notificationsService.send(
                event.projectId(), event.recipientEmail(),
                null, subject, body, null, null);
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
