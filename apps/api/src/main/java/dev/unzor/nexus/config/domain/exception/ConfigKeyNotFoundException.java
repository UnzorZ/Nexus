package dev.unzor.nexus.config.domain.exception;

/** Clave de configuración inexistente en el proyecto. → 404 resource_not_found. */
public class ConfigKeyNotFoundException extends RuntimeException {
    public ConfigKeyNotFoundException(String message) {
        super(message);
    }
}
