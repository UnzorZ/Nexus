package dev.unzor.nexus.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hashes criptográficos shared (SHA-256 hex) para verificadores opacos de un solo uso
 * (tokens de verify/reset, recovery codes MFA). El valor en claro nunca se persiste: se
 * guarda su hash, de modo que un compromiso del almacenamiento no expone verificadores
 * válidos.
 *
 * <p>Compartido por el portal de usuario final (identity) y el panel (admin) en
 * {@code shared.security}.</p>
 */
public final class SecureHashes {

    private SecureHashes() {
    }

    /** Hash SHA-256 hex (64 caracteres) del valor en claro. */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
