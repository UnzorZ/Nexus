package dev.unzor.nexus.shared.audit;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/**
 * Evento de auditoría genérico (ADR-0004, módulo {@code audit}). Lo publica
 * cualquier módulo que mute estado de un proyecto (api keys, members, roles,
 * permissions, modules, projects) o el filtro de auth al rechazar credenciales;
 * el módulo {@code audit} lo consume y lo persiste en {@code audit_log}. Nunca
 * lleva secretos ni hashes.
 * <p>
 * Es inmutable y autodescriptivo: actor, recurso afectado, severidad, contexto
 * de la petición ({@code ip}, {@code userAgent}, {@code traceId} leídos de MDC
 * por la factoría) y metadata libre. Las factorías {@link #byAccount} y
 * {@link #anonymous} centralizan la lectura de MDC y DERIVAN la severidad del
 * {@code action} ({@link Severity#forAction(String)}) para que los puntos de
 * emisión queden en una sola línea.
 */
public record AuditEvent(
        UUID projectId,
        String action,
        String resourceType,
        String resourceId,
        Severity severity,
        String actorType,
        String actorId,
        String ip,
        String userAgent,
        String traceId,
        Map<String, Object> metadata
) {

    /** Actor {@code NEXUS_ACCOUNT}: una cuenta Nexus autenticada vía panel. */
    public static AuditEvent byAccount(
            UUID projectId,
            String action,
            String resourceType,
            String resourceId,
            UUID actorAccountId,
            Map<String, Object> metadata
    ) {
        return new AuditEvent(
                projectId,
                action,
                resourceType,
                resourceId,
                Severity.forAction(action),
                "NEXUS_ACCOUNT",
                actorAccountId == null ? null : actorAccountId.toString(),
                MDC.get("ip"),
                MDC.get("userAgent"),
                MDC.get("traceId"),
                metadata);
    }

    /** Actor {@code ANONYMOUS}: p. ej. un rechazo de auth sin actor identificado. */
    public static AuditEvent anonymous(
            UUID projectId,
            String action,
            String resourceType,
            String resourceId,
            String reason
    ) {
        return new AuditEvent(
                projectId,
                action,
                resourceType,
                resourceId,
                Severity.forAction(action),
                "ANONYMOUS",
                null,
                MDC.get("ip"),
                MDC.get("userAgent"),
                MDC.get("traceId"),
                reason == null ? null : Map.of("reason", reason));
    }
}
