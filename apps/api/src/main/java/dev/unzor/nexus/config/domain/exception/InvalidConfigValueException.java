package dev.unzor.nexus.config.domain.exception;

/** El valor no se ajusta al {@code ConfigValueType} declarado. → 400 validation_error. */
public class InvalidConfigValueException extends RuntimeException {
    public InvalidConfigValueException(String message) {
        super(message);
    }
}
