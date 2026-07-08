package dev.unzor.nexus.admin.infrastructure.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Ticket efímero de login MFA pendiente del panel: la contraseña ya se verificó pero
 * falta el segundo factor TOTP de la cuenta Nexus. Se guarda como atributo de sesión
 * ({@code nexus.panelMfaPending}) —nunca como {@code SecurityContext}— para que ninguna
 * petición autenticada se considere válida durante la ventana. Espejo panel-local de
 * {@code MfaPendingTicket} del portal de usuario final (no comparten tipo ni clave).
 *
 * <p>Serializable porque Spring Session Redis serializa los atributos vía JDK.</p>
 */
public record PanelMfaPendingTicket(
        UUID accountId,
        Instant passwordVerifiedAt,
        Instant expiresAt
) implements Serializable {
}
