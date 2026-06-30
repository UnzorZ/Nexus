package dev.unzor.nexus.registry.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de {@code POST /api/v1/registry/heartbeat} (spec §13.1):
 * {@code receivedAt} es el instante en que Nexus registró el latido y
 * {@code nextHeartbeatInSeconds} el intervalo sugerido para el próximo.
 */
public record HeartbeatReceipt(
        UUID projectId,
        Instant receivedAt,
        int nextHeartbeatInSeconds
) {
}
