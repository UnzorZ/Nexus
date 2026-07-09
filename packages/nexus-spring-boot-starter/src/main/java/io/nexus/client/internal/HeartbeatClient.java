package io.nexus.client.internal;

import io.nexus.client.api.HeartbeatReceipt;
import io.nexus.client.api.InstanceToken;

import java.util.Map;

/**
 * Cliente del latido de instancia ({@code /api/v1/registry}). {@link #register()}
 * hace el handshake ADR-1212 (API key cruda → instance token efímero); los latidos
 * posteriores pueden usar ese token vía {@link NexusHttpClient#useInstanceToken}.
 */
public class HeartbeatClient {

    private final NexusHttpClient http;

    public HeartbeatClient(NexusHttpClient http) {
        this.http = http;
    }

    public InstanceToken register() {
        return http.post("/api/v1/registry/register", Map.of(), InstanceToken.class);
    }

    public HeartbeatReceipt heartbeat(String instanceId, String appName, String appVersion, String status) {
        return http.post("/api/v1/registry/heartbeat",
                Map.of(
                        "instanceId", instanceId,
                        "appName", appName,
                        "appVersion", appVersion == null ? "" : appVersion,
                        "status", status == null ? "up" : status,
                        "metadata", Map.of()),
                HeartbeatReceipt.class);
    }
}
