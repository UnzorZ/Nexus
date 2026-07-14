package dev.unzor.nexus.sdk.internal;

import dev.unzor.nexus.sdk.api.NotifyMessage;

import java.util.Map;

/**
 * Cliente de notificaciones ({@code /api/v1/notify/send}, scope {@code notify:send}).
 */
public class NotifyClient {

    private final NexusHttpClient http;

    public NotifyClient(NexusHttpClient http) {
        this.http = http;
    }

    public void send(NotifyMessage message) {
        http.post("/api/v1/notify/send",
                Map.of(
                        "to", message.to(),
                        "templateName", message.templateName() == null ? "" : message.templateName(),
                        "subject", message.subject() == null ? "" : message.subject(),
                        "body", message.body() == null ? "" : message.body(),
                        "variables", message.variables() == null ? Map.of() : message.variables()),
                Void.class);
    }
}
