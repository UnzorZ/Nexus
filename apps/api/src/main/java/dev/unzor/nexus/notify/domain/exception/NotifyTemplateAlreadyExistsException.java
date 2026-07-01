package dev.unzor.nexus.notify.domain.exception;

public class NotifyTemplateAlreadyExistsException extends RuntimeException {
    public NotifyTemplateAlreadyExistsException(String message) {
        super(message);
    }
}
