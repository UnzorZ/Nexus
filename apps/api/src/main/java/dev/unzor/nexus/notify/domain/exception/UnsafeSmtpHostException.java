package dev.unzor.nexus.notify.domain.exception;

/**
 * El host SMTP resuelve a una dirección no pública (loopback, privada,
 * link-local/metadata, ...). En una instancia multi-tenant esto bloquea el
 * SSRF: un usuario malicioso no puede apuntar el SMTP a servicios internos.
 */
public class UnsafeSmtpHostException extends RuntimeException {
    public UnsafeSmtpHostException(String message) {
        super(message);
    }
}
