package dev.unzor.nexus.vault.domain.exception;

public class VaultSecretNotFoundException extends RuntimeException {
    public VaultSecretNotFoundException(String message) {
        super(message);
    }
}
