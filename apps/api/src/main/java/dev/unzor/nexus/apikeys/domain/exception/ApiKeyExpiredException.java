package dev.unzor.nexus.apikeys.domain.exception;

/**
 * Lanzada en runtime cuando la API key ha expirado ({@code expires_at} pasada).
 * Se traduce a {@code 401 api_key_expired} (spec §11).
 */
public class ApiKeyExpiredException extends RuntimeException {

    public ApiKeyExpiredException() {
        super("API key has expired");
    }
}
