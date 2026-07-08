package dev.unzor.nexus.identity.infrastructure;

import dev.unzor.nexus.shared.security.SecureHashes;

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

    /**
     * Hash SHA-256 hex del token en claro (para almacenamiento / lookup). Delega en el
     * util compartido {@link SecureHashes#sha256Hex}.
     */
    public static String hash(String rawToken) {
        return SecureHashes.sha256Hex(rawToken);
    }
}

