package dev.unzor.nexus.identity.domain.exception;

/**
 * El token de verificación de email no existe, ya fue consumido o está expirado.
 * No distingue causa para no filtrar estado (se reenvía uno nuevo a petición).
 */
public class InvalidEmailVerificationTokenException extends RuntimeException {
    public InvalidEmailVerificationTokenException() {
        super("Invalid or expired email verification token.");
    }
}
