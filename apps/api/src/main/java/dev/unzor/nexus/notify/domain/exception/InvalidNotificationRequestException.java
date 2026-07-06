package dev.unzor.nexus.notify.domain.exception;

/** Falta templateName o (subject + body) en un envío. → 400 validation_error. */
public class InvalidNotificationRequestException extends RuntimeException {
    public InvalidNotificationRequestException(String message) {
        super(message);
    }
}
