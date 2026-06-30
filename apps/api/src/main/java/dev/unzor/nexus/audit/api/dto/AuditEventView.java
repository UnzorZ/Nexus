package dev.unzor.nexus.audit.api.dto;

import dev.unzor.nexus.audit.domain.entity.AuditLogEntry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Vista de un evento de auditoría para el panel. {@code outcome} es el nombre
 * del enum ({@code SUCCESS}/{@code FAILURE}) como string para el JSON. La
 * metadata libre se devuelve tal cual (sin secretos: el evento nunca los lleva).
 */
public record AuditEventView(
        UUID id,
        UUID projectId,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        String actorType,
        String actorId,
        String ip,
        String userAgent,
        String traceId,
        Instant occurredAt,
        Map<String, Object> metadata
) {

    public static AuditEventView from(AuditLogEntry entry) {
        return new AuditEventView(
                entry.getId(),
                entry.getProjectId(),
                entry.getAction(),
                entry.getResourceType(),
                entry.getResourceId(),
                entry.getOutcome().name(),
                entry.getActorType(),
                entry.getActorId(),
                entry.getIp(),
                entry.getUserAgent(),
                entry.getTraceId(),
                entry.getOccurredAt(),
                entry.getMetadata());
    }
}
