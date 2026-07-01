package dev.unzor.nexus.vault.domain.exception;

/** Cifrado solicitado no soportado por el vault. */
public class InvalidCipherException extends RuntimeException {
    public InvalidCipherException(String message) {
        super(message);
    }
}
