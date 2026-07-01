package dev.unzor.nexus.vault.domain.exception;

public class VaultSecretAlreadyExistsException extends RuntimeException {
    public VaultSecretAlreadyExistsException(String message) {
        super(message);
    }
}
