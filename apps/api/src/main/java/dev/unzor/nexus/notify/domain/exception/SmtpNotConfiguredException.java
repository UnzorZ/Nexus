package dev.unzor.nexus.notify.domain.exception;

/** SMTP sin configurar: el envío no puede intentarse. */
public class SmtpNotConfiguredException extends RuntimeException {
    public SmtpNotConfiguredException(String message) {
        super(message);
    }
}
