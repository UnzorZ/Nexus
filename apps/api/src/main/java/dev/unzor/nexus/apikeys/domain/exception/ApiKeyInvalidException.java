package dev.unzor.nexus.apikeys.domain.exception;

/**
 * Lanzada en runtime cuando una API key no existe o su hash no coincide.
 * Se traduce a {@code 401 invalid_api_key} (spec §11).
 */
public class ApiKeyInvalidException extends RuntimeException {

    public ApiKeyInvalidException() {
        super("Invalid API key");
    }
}
