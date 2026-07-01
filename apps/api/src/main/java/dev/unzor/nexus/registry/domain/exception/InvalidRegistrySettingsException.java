package dev.unzor.nexus.registry.domain.exception;

/** Umbrales de liveness inválidos (p. ej. stale < interval o timeout < stale). */
public class InvalidRegistrySettingsException extends RuntimeException {
    public InvalidRegistrySettingsException(String message) {
        super(message);
    }
}
