package dev.unzor.nexus.notify.domain.exception;

public class NotifyTemplateNotFoundException extends RuntimeException {
    public NotifyTemplateNotFoundException(String message) {
        super(message);
    }
}
