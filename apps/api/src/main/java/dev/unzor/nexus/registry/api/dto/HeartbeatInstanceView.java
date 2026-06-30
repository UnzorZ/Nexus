package dev.unzor.nexus.registry.api.dto;

import dev.unzor.nexus.registry.domain.entity.ProjectHeartbeat;
import dev.unzor.nexus.registry.domain.enums.HeartbeatLiveness;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de una instancia para el listado del panel. {@code status} es el valor
 * auto-reportado por la instancia; {@code liveness} es la ONLINE/STALE/OFFLINE
 * derivada de {@code lastSeenAt} y el timeout. {@code apiKeyPrefix} es el prefijo
 * no sensible de la key que reportó ({@code nxs_<slug>_<partial>}); el secreto y
 * el hash nunca se exponen.
 */
public record HeartbeatInstanceView(
        UUID id,
        String instanceId,
        String appName,
        String appVersion,
        String status,
        HeartbeatLiveness liveness,
        Instant lastSeenAt,
        String apiKeyPrefix,
        Instant createdAt
) {

    public static HeartbeatInstanceView from(ProjectHeartbeat beat, HeartbeatLiveness liveness) {
        return new HeartbeatInstanceView(
                beat.getId(),
                beat.getInstanceId(),
                beat.getAppName(),
                beat.getAppVersion(),
                beat.getStatus(),
                liveness,
                beat.getLastSeenAt(),
                beat.getApiKeyPrefix(),
                beat.getCreatedAt()
        );
    }
}
