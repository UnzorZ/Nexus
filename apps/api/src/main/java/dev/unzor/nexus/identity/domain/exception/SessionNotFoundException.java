package dev.unzor.nexus.identity.domain.exception;

/**
 * Se lanza cuando no existe una sesión con el identificador público indicado para el
 * usuario de proyecto autenticado, o bien no pertenece a ese usuario.
 *
 * <p>Se mapea a {@code 404 Not Found} para no revelar si la sesión existía pero era de
 * otro usuario.</p>
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
