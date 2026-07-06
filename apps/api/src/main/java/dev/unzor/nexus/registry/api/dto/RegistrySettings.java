package dev.unzor.nexus.registry.api.dto;

import dev.unzor.nexus.registry.domain.entity.ProjectRegistrySettings;

import java.time.Instant;
import java.util.UUID;

/**
 * Umbrales de liveness de heartbeat de un proyecto + config de alerta offline. Si
 * el proyecto no tiene override, se devuelven los defaults globales con
 * {@code overridden=false}.
 */
public record RegistrySettings(
        UUID projectId,
        int intervalSeconds,
        int timeoutSeconds,
        boolean offlineNotifyEnabled,
        String offlineNotifyEmail,
        boolean overridden,
        Instant updatedAt
) {
    public static RegistrySettings defaults(UUID projectId, int interval, int timeout) {
        return new RegistrySettings(projectId, interval, timeout, false, null, false, null);
    }

    public static RegistrySettings from(ProjectRegistrySettings settings) {
        return new RegistrySettings(settings.getProjectId(), settings.getIntervalSeconds(),
                settings.getTimeoutSeconds(), settings.isOfflineNotifyEnabled(),
                settings.getOfflineNotifyEmail(), true, settings.getUpdatedAt());
    }
}
