package dev.unzor.nexus.vault.domain.enums;

import dev.unzor.nexus.vault.domain.exception.InvalidCipherException;

/**
 * Algoritmos AEAD soportados por el vault. Ambos usan nonce de 12 bytes y tag
 * de 128 bits; la clave AES/ChaCha20 se deriva por SHA-256 de la master key.
 */
public enum VaultCipher {
    AES_256_GCM("AES/GCM/NoPadding", "AES"),
    CHACHA20_POLY1305("ChaCha20-Poly1305", "ChaCha20");

    public final String transformation;
    public final String keyAlgorithm;

    VaultCipher(String transformation, String keyAlgorithm) {
        this.transformation = transformation;
        this.keyAlgorithm = keyAlgorithm;
    }

    public static VaultCipher fromKey(String key) {
        if (key == null) {
            return AES_256_GCM;
        }
        for (VaultCipher cipher : values()) {
            if (cipher.name().equalsIgnoreCase(key)) {
                return cipher;
            }
        }
        throw new InvalidCipherException("Unsupported cipher: " + key);
    }
}
