package dev.unzor.nexus.apikeys.application.events;

import java.util.Map;
import java.util.UUID;

/**
 * Evento de auditoría del ciclo de vida de una API key (ADR-0004). Lo emiten
 * las mutations del servicio (create/rotate/disable/enable/delete) y los
 * rechazos del filtro de runtime (auth inválida/deshabilitada/expirada). Nunca
 * transporta el secreto ni el hash: solo identificadores y metadatos pequeños
 * (p. ej. nombre y prefijo de la key).
 *
 * <p>Se publica como evento de aplicación Spring; el módulo {@code audit} lo
 * persistirá cuando se implemente (su milestone). Mientras tanto,
 * {@link ApiKeyAuditLogger} lo registra en log para que no se pierda.</p>
 */
public record ApiKeyAuditEvent(
        String action,
        UUID projectId,
        UUID keyId,
        String actorType,
        String actorId,
        String reason,
        Map<String, Object> metadata
) {
}
