package dev.unzor.nexus.admin.domain.exception;

/**
 * Se lanza cuando no existe una sesión con el identificador público indicado para la
 * cuenta autenticada, o bien no pertenece a esa cuenta.
 *
 * <p>Se mapea a {@code 404 Not Found} con el código {@code session_not_found} para no
 * revelar si la sesión existía pero era de otra cuenta.</p>
 */
public class SessionNotFoundException extends RuntimeException {

    private final String publicSessionId;

    public SessionNotFoundException(String publicSessionId) {
        super("Session not found: " + publicSessionId);
        this.publicSessionId = publicSessionId;
    }

    public String getPublicSessionId() {
        return publicSessionId;
    }
}
