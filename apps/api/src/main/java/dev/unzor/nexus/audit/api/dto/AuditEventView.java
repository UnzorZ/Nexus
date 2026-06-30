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
        String actorDisplayName,
        String actorEmail,
        Boolean actorAdmin,
        String ip,
        String userAgent,
        String traceId,
        Instant occurredAt,
        Map<String, Object> metadata
) {

    public static AuditEventView from(AuditLogEntry entry) {
        return from(entry, null, null, null);
    }

    /**
     * Vista enriquecida con el {@code displayName}/email del actor cuando es una
     * cuenta Nexus (resuelto vía {@code AccountDirectory} en el servicio de
     * lectura). Los eventos anónimos o sin cuenta dejan ambos a {@code null}; el
     * UI los trata mostrando el actor crudo o un guion.
     */
    public static AuditEventView from(
            AuditLogEntry entry,
            String actorDisplayName,
            String actorEmail,
            Boolean actorAdmin) {
        return new AuditEventView(
                entry.getId(),
                entry.getProjectId(),
                entry.getAction(),
                entry.getResourceType(),
                entry.getResourceId(),
                entry.getOutcome().name(),
                entry.getActorType(),
                entry.getActorId(),
                actorDisplayName,
                actorEmail,
                actorAdmin,
                entry.getIp(),
                entry.getUserAgent(),
                entry.getTraceId(),
                entry.getOccurredAt(),
                entry.getMetadata());
    }
}
