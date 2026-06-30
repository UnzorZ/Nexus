package dev.unzor.nexus.apikeys.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generación y verificación de API keys (spec §9.3, §21.1).
 *
 * <p>Formato: {@code nxs_<projectSlug>_<secret>}, donde el secreto es base64url
 * de 256 bits. Se persisten {@code keyPrefix} (primeros {@value #PREFIX_LENGTH}
 * caracteres del secreto, para búsqueda) y {@code keyHash} (SHA-256 hex de la
 * key completa). La verificación usa {@link MessageDigest#isEqual} (tiempo
 * constante). El secreto completo solo se devuelve al crear/rotar.</p>
 */
@Component
public class ApiKeyHasher {

    static final int PREFIX_LENGTH = 12;
    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    public GeneratedKey generate(String projectSlug) {
        String secret = urlToken();
        String fullKey = "nxs_" + projectSlug + "_" + secret;
        String keyPrefix = secret.substring(0, Math.min(PREFIX_LENGTH, secret.length()));
        String keyHash = sha256Hex(fullKey);
        return new GeneratedKey(fullKey, keyPrefix, keyHash);
    }

    /**
     * Extrae el prefijo de búsqueda de una key cruda. La key es
     * {@code nxs_<slug>_<secret>}; como el slug es {@code [a-z0-9-]} (sin
     * guiones bajos), {@code split("_", 3)} separa de forma estable
     * {@code [nxs, slug, secret]}.
     */
    public String prefixOf(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        String[] parts = rawKey.split("_", 3);
        if (parts.length < 3 || parts[2].isEmpty()) {
            return null;
        }
        String secret = parts[2];
        return secret.length() >= PREFIX_LENGTH ? secret.substring(0, PREFIX_LENGTH) : secret;
    }

    /** Comparación de tiempo constante entre la key cruda y el hash almacenado. */
    public boolean verify(String rawKey, String expectedHash) {
        if (rawKey == null || expectedHash == null) {
            return false;
        }
        String computed = sha256Hex(rawKey);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String urlToken() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    /** Resultado de generar una key: secreto completo (mostrar una vez), prefijo y hash. */
    public record GeneratedKey(String fullKey, String keyPrefix, String keyHash) {
    }
}
