package dev.unzor.nexus.admin.domain.events;

import java.util.UUID;

/**
 * Evento de dominio publicado cuando una cuenta debe cerrar todas sus sesiones del
 * panel de forma inmediata.
 *
 * <p>Lo publica el agregado {@code dev.unzor.nexus.admin.domain.entity.NexusAccount}
 * al suspenderlo o desactivarlo, o al retirar el flag {@code instanceAdmin}; está
 * pensado también para el futuro cambio de contraseña. El módulo {@code admin}
 * escucha este evento y revoca las sesiones a través de
 * {@code PanelSessionService.revokeAllForAccount(accountId)}.</p>
 *
 * <p>Vive en {@code domain.events} para que la entidad de dominio pueda publicarlo sin
 * depender de la capa de aplicación. La entrega es <em>al menos una vez</em>: Spring
 * Modulith persiste las publicaciones en PostgreSQL y la reentrega periódica garantiza
 * que una revocación se complete aunque Redis falle justo después del commit. La
 * operación de revocación es idempotente.</p>
 *
 * @param accountId identificador de la cuenta cuyas sesiones deben revocarse
 */
public record NexusAccountSessionsRevocationRequested(UUID accountId) {
}
