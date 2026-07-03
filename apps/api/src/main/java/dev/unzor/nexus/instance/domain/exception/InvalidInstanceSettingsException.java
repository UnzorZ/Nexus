package dev.unzor.nexus.instance.domain.exception;

/**
 * Validación de un valor writeable de configuración de instancia (heartbeat,
 * etc.). Se traduce a 400 {@code validation_error}.
 */
public class InvalidInstanceSettingsException extends RuntimeException {

    public InvalidInstanceSettingsException(String message) {
        super(message);
    }
}
