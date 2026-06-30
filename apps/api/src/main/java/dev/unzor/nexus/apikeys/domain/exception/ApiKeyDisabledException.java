package dev.unzor.nexus.apikeys.domain.exception;

/**
 * Lanzada en runtime cuando la API key está deshabilitada.
 * Se traduce a {@code 401 api_key_disabled} (spec §11).
 */
public class ApiKeyDisabledException extends RuntimeException {

    public ApiKeyDisabledException() {
        super("API key is disabled");
    }
}
