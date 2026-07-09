package dev.unzor.nexus.identity.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot de autorización de un usuario de proyecto (spec §14.11). Lo sirve
 * {@code GET /api/v1/authz/users/{userId}/snapshot} para que el SDK de Nexus
 * (spec §18) resuelva permisos localmente durante el TTL del snapshot en vez de
 * ir al backend en cada decisión.
 *
 * <p>Snapshot optimista: válido hasta {@code expiresAt}. Un cambio de rol bumpa
 * {@code authzVersion}; el SDK debe re-fetchear cuando lo detecte. Para una
 * decisión autoritativa fresca, el SDK consulta de nuevo (fail-closed si caducó
 * y Nexus no responde).</p>
 *
 * @param userId       usuario del proyecto.
 * @param projectId    proyecto (del API key resuelto).
 * @param authzVersion versión de autorización vigente del usuario; {@code -1} si
 *                     no existe (usuario borrado) → el SDK debe denegar.
 * @param roles        claves de los roles asignados.
 * @param permissions  claves de permiso efectivas (comodines verbatim:
 *                     {@code orders.*}, {@code *}).
 * @param expiresAt    instante hasta el que el snapshot es válido.
 */
public record AuthorizationSnapshot(
        UUID userId,
        UUID projectId,
        long authzVersion,
        List<String> roles,
        List<String> permissions,
        Instant expiresAt
) {
}
