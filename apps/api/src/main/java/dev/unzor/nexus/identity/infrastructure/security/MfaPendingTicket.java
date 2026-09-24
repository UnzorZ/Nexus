package dev.unzor.nexus.identity.infrastructure.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Ticket efímero de login MFA pendiente: el primer factor ya se verificó (identidad
 * confirmada — contraseña o proveedor externo Google) pero falta el segundo factor TOTP.
 * Se guarda como atributo de sesión ({@code nexus.mfaPending}) —nunca como
 * {@code SecurityContext}— para que el Authorization Server no pueda reanudar
 * {@code /oauth2/authorize} durante la ventana.
 *
 * <p>{@link #primaryFactor} registra cómo se probó el primer factor
 * ({@link #FACTOR_PASSWORD} o {@link #FACTOR_GOOGLE}): al completar el TOTP, el factor
 * primario de la sesión (y el {@code authMethod} auditado) salen de aquí, de modo que un
 * login federado con MFA no se disfraza de login con contraseña.</p>
 *
 * <p>Serializable porque Spring Session Redis serializa los atributos de sesión vía
 * serialización JDK.</p>
 */
public record MfaPendingTicket(
        UUID userId,
        UUID projectId,
        Instant passwordVerifiedAt,
        Instant expiresAt,
        String primaryFactor
) implements Serializable {

    /** Primer factor: contraseña del proyecto. */
    public static final String FACTOR_PASSWORD = "password";

    /** Primer factor: identidad verificada por el proveedor externo Google. */
    public static final String FACTOR_GOOGLE = "google";
}
