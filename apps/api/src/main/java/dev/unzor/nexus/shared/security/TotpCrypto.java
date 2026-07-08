package dev.unzor.nexus.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

/**
 * Cifrado simétrico AES-256-GCM para el secret TOTP. El secret debe ser
 * <b>reversible</b> (hace falta el plaintext para computar los códigos), así que se
 * guarda cifrado, no hasheado. Reutiliza la master key global
 * {@code nexus.vault.master-key} derivando la clave AES por SHA-256 — idéntico a
 * {@code NotifyCrypto}.
 *
 * <p>Vive en {@code shared.security} para que tanto el portal de usuario final (identity)
 * como el panel (admin) compartan el mismo cifrado del secret TOTP.</p>
 *
 * <p><b>Fail-closed:</b> si la master key está en blanco, o es el default de dev fuera
 * de perfiles de desarrollo, el bean falla al construirse.</p>
 */
@Component
public class TotpCrypto {

    public static final String DEV_DEFAULT_MASTER_KEY = "nexus-dev-vault-master-key-do-not-use-in-prod";
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String SEPARATOR = ".";
    private static final Set<String> DEV_PROFILES = Set.of("dev", "local", "test", "remote-dev");

    private final SecretKey aesKey;
    private final SecureRandom random = new SecureRandom();

    public TotpCrypto(
            @Value("${nexus.vault.master-key:nexus-dev-vault-master-key-do-not-use-in-prod}") String masterKey,
            Environment environment) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("nexus.vault.master-key must be set.");
        }
        if (DEV_DEFAULT_MASTER_KEY.equals(masterKey) && !isDevProfile(environment)) {
            throw new IllegalStateException(
                    "nexus.vault.master-key must be overridden from the dev default outside dev profiles.");
        }
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            this.aesKey = new SecretKeySpec(derived, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to derive TOTP AES key", exception);
        }
    }

    /** Cifra y devuelve {@code base64(nonce).base64(ciphertext)}. */
    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder b64 = Base64.getEncoder();
            return b64.encodeToString(nonce) + SEPARATOR + b64.encodeToString(ciphertext);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", exception);
        }
    }

    public String decrypt(String stored) {
        try {
            int sep = stored.indexOf(SEPARATOR);
            if (sep < 0) {
                throw new IllegalArgumentException("Malformed ciphertext");
            }
            Base64.Decoder b64 = Base64.getDecoder();
            byte[] nonce = b64.decode(stored.substring(0, sep));
            byte[] ciphertext = b64.decode(stored.substring(sep + 1));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt TOTP secret", exception);
        }
    }

    private static boolean isDevProfile(Environment environment) {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true;
        }
        return Arrays.stream(active).anyMatch(DEV_PROFILES::contains);
    }
}
