package dev.unzor.nexus.vault.application.service;

import dev.unzor.nexus.vault.application.configuration.VaultProperties;
import dev.unzor.nexus.vault.domain.enums.VaultCipher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

/**
 * Cifrado AEAD de los secretos del vault. Soporta AES-256-GCM (por defecto) y
 * ChaCha20-Poly1305. La clave se deriva por SHA-256 de una master key (passphrase
 * arbitraria) — la global de {@code nexus.vault.master-key}, o un override por
 * proyecto resuelto por {@link VaultKeyResolver}.
 *
 * <p><b>Fail-closed:</b> si la master key global está en blanco, o es el default
 * de dev bajo cualquier perfil que no sea explícitamente de desarrollo, el bean
 * falla al construirse; nunca se cifran secretos con una clave conocida fuera de
 * local/test.</p>
 */
@Component
public class VaultCrypto {

    public static final String DEV_DEFAULT_MASTER_KEY = "nexus-dev-vault-master-key-do-not-use-in-prod";
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final Set<String> DEV_PROFILES = Set.of("dev", "local", "test", "remote-dev");

    private final String globalMasterKey;
    private final SecureRandom random = new SecureRandom();

    public VaultCrypto(VaultProperties properties, Environment environment) {
        String masterKey = properties.masterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("nexus.vault.master-key must be set.");
        }
        if (DEV_DEFAULT_MASTER_KEY.equals(masterKey) && !isDevProfile(environment)) {
            throw new IllegalStateException(
                    "nexus.vault.master-key must be overridden from the dev default outside dev profiles.");
        }
        this.globalMasterKey = masterKey;
    }

    private static boolean isDevProfile(Environment environment) {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true;
        }
        return Arrays.stream(active).anyMatch(DEV_PROFILES::contains);
    }

    /** Master key global de la instancia (para wrapping de overrides y fallback). */
    public String globalMasterKey() {
        return globalMasterKey;
    }

    /** Deriva la clave AEAD (32 bytes vía SHA-256) para el algoritmo del cipher. */
    public SecretKey deriveKey(String masterKey, VaultCipher cipher) {
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, cipher.keyAlgorithm);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to derive vault key", exception);
        }
    }

    public Encrypted encrypt(String plaintext, VaultCipher cipher, String masterKey) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher c = Cipher.getInstance(cipher.transformation);
            c.init(Cipher.ENCRYPT_MODE, deriveKey(masterKey, cipher), aeadParams(cipher, nonce));
            byte[] ciphertext = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder b64 = Base64.getEncoder();
            return new Encrypted(b64.encodeToString(ciphertext), b64.encodeToString(nonce));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt vault secret", exception);
        }
    }

    public String decrypt(String ciphertextBase64, String nonceBase64, VaultCipher cipher, String masterKey) {
        try {
            Base64.Decoder b64 = Base64.getDecoder();
            byte[] nonce = b64.decode(nonceBase64);
            Cipher c = Cipher.getInstance(cipher.transformation);
            c.init(Cipher.DECRYPT_MODE, deriveKey(masterKey, cipher), aeadParams(cipher, nonce));
            return new String(c.doFinal(b64.decode(ciphertextBase64)), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt vault secret", exception);
        }
    }

    private static java.security.spec.AlgorithmParameterSpec aeadParams(VaultCipher cipher, byte[] nonce) {
        return cipher == VaultCipher.AES_256_GCM
                ? new GCMParameterSpec(GCM_TAG_BITS, nonce)
                : new IvParameterSpec(nonce);
    }

    /** Par ciphertext+nonce en base64 resultado de {@link #encrypt}. */
    public record Encrypted(String ciphertextBase64, String nonceBase64) {
    }
}
