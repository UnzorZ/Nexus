package dev.unzor.nexus.notify.api.dto;

import dev.unzor.nexus.notify.domain.entity.ProjectNotifyVariables;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Variables globales de notificación de un proyecto. */
public record GlobalVariables(
        UUID projectId,
        Map<String, String> variables,
        Instant updatedAt
) {
    public static GlobalVariables from(ProjectNotifyVariables entity) {
        return new GlobalVariables(entity.getProjectId(), entity.getVariables(), entity.getUpdatedAt());
    }
}
