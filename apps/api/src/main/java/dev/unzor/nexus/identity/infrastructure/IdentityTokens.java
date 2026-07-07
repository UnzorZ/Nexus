package dev.unzor.nexus.identity.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generación y hash de tokens opacos de un solo uso (verificación de email, reseteo de
 * contraseña). El token en claro viaja únicamente por el enlace del email; en base de
 * datos se guarda su hash SHA-256 hex, de modo que un compromiso del almacenamiento no
 * expone tokens válidos.
 */
public final class IdentityTokens {

    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private IdentityTokens() {
    }

    /** Token aleatorio opaco (32 bytes, base64url sin padding). */
    public static String generate() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /** Hash SHA-256 hex del token en claro (para almacenamiento / lookup). */
    public static String hash(String rawToken) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(rawToken.getBytes(StandardCharsets.UTF_8));
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
