package dev.unzor.nexus.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista inmutable y segura de una sesión del panel.
 *
 * <p>El identificador expuesto ({@code id}) es el identificador público de gestión
 * ({@code nexus.sessionPublicId}), nunca el ID interno de Spring Session ni el valor de
 * la cookie {@code JSESSIONID}. El instante de expiración se calcula como
 * {@code lastAccessedAt + maxInactiveIntervalSeconds}.</p>
 *
 * @param id identificador público de gestión de la sesión
 * @param current si es la sesión con la que se hizo la petición actual
 * @param userAgent cabecera {@code User-Agent} truncada registrada en el login
 * @param createdAt instante de creación de la sesión
 * @param lastAccessedAt instante del último acceso
 * @param expiresAt instante estimado de expiración por inactividad
 * @param maxInactiveIntervalSeconds inactividad máxima antes de expirar
 */
public record SessionSummary(
        UUID id,
        boolean current,
        String userAgent,
        Instant createdAt,
        Instant lastAccessedAt,
        Instant expiresAt,
        int maxInactiveIntervalSeconds
) {
}
