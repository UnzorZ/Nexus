package dev.unzor.nexus.vault.application.service;

import dev.unzor.nexus.vault.application.configuration.VaultProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado simétrico AES-256-GCM de los secretos del vault. La clave AES se
 * deriva por SHA-256 de {@code nexus.vault.master-key} (passphrase arbitraria).
 *
 * <p><b>Fail-closed:</b> si la clave maestra está en blanco, o es el default de
 * dev bajo el perfil {@code prod}, el bean falla al construirse (la app no
 * arranca) — nunca se cifran secretos con una clave conocida en producción.</p>
 */
@Component
public class VaultCrypto {

    public static final String DEV_DEFAULT_MASTER_KEY = "nexus-dev-vault-master-key-do-not-use-in-prod";
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey aesKey;
    private final SecureRandom random = new SecureRandom();

    public VaultCrypto(VaultProperties properties, Environment environment) {
        String masterKey = properties.masterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("nexus.vault.master-key must be set.");
        }
        if (DEV_DEFAULT_MASTER_KEY.equals(masterKey) && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "nexus.vault.master-key must be overridden from the dev default in the 'prod' profile.");
        }
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            this.aesKey = new SecretKeySpec(derived, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to derive vault AES key", exception);
        }
    }

    public Encrypted encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Encrypted(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(nonce));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt vault secret", exception);
        }
    }

    public String decrypt(String ciphertextBase64, String nonceBase64) {
        try {
            byte[] nonce = Base64.getDecoder().decode(nonceBase64);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt vault secret", exception);
        }
    }

    /** Par ciphertext+nonce en base64 resultado de {@link #encrypt}. */
    public record Encrypted(String ciphertextBase64, String nonceBase64) {
    }
}
