package io.nexus.client.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Acuse de latido ({@code POST /api/v1/registry/heartbeat}), espejo del backend.
 */
public record HeartbeatReceipt(UUID projectId, Instant receivedAt, int nextHeartbeatInSeconds) {
}
