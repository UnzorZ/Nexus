package dev.unzor.nexus.identity.domain.exception;

/**
 * El token de reseteo de contraseña no existe, ya fue consumido o está expirado.
 * No distingue causa (anti-filtrado de estado).
 */
public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException() {
        super("Invalid or expired password reset token.");
    }
}
