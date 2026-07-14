package dev.unzor.nexus.sdk.api;

import java.util.Map;

/**
 * Mensaje a enviar vía {@code POST /api/v1/notify/send} (scope {@code notify:send}).
 * Se requiere {@code to} y, o bien {@code templateName} (renderiza la plantilla
 * con {@code variables}), o {@code subject} + {@code body} en línea.
 */
public record NotifyMessage(String to, String templateName, String subject, String body, Map<String, String> variables) {

    public static NotifyMessage plain(String to, String subject, String body) {
        return new NotifyMessage(to, null, subject, body, Map.of());
    }
}
