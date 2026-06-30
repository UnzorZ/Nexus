package dev.unzor.nexus.apikeys.domain.exception;

/**
 * Lanzada cuando no se encuentra una API key en la gestión del panel.
 */
public class ApiKeyNotFoundException extends RuntimeException {

    public ApiKeyNotFoundException(String message) {
        super(message);
    }
}
