package dev.unzor.nexus.identity.infrastructure.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Ticket efímero de login MFA pendiente: la contraseña ya se verificó (identidad
 * confirmada) pero falta el segundo factor TOTP. Se guarda como atributo de sesión
 * ({@code nexus.mfaPending}) —nunca como {@code SecurityContext}— para que el
 * Authorization Server no pueda reanudar {@code /oauth2/authorize} durante la ventana.
 *
 * <p>Serializable porque Spring Session Redis serializa los atributos de sesión vía
 * serialización JDK.</p>
 */
public record MfaPendingTicket(
        UUID userId,
        UUID projectId,
        Instant passwordVerifiedAt,
        Instant expiresAt
) implements Serializable {
}
