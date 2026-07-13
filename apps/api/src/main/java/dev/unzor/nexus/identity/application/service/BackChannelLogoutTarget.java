package dev.unzor.nexus.identity.application.service;

import java.util.UUID;

/**
 * Snapshot inmutable de un cliente al que hay que enviar un logout token.
 *
 * <p>Se captura antes de eliminar la autorización OAuth para que la entrega asíncrona no
 * dependa de que siga existiendo el tracking sesión→cliente.</p>
 */
public record BackChannelLogoutTarget(UUID id, String clientId, String logoutUri) {
}
