package dev.unzor.nexus.identity.domain.exception;

/**
 * La nueva contraseña no cumple la política mínima (longitud).
 */
public class WeakPasswordException extends RuntimeException {
    public WeakPasswordException() {
        super("Password does not meet the minimum policy.");
    }
}
