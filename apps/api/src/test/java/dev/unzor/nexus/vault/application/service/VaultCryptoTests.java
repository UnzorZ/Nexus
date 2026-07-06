package dev.unzor.nexus.vault.application.service;

import dev.unzor.nexus.vault.application.configuration.VaultProperties;
import dev.unzor.nexus.vault.domain.enums.VaultCipher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VaultCryptoTests {

    @Test
    void permitsDevDefaultUnderDefaultProfile() {
        assertThatCode(() -> crypto(VaultCrypto.DEV_DEFAULT_MASTER_KEY))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsDevDefaultUnderKnownDevProfile() {
        assertThatCode(() -> crypto(VaultCrypto.DEV_DEFAULT_MASTER_KEY, "remote-dev"))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesDevDefaultUnderStagingProfile() {
        assertThatThrownBy(() -> crypto(VaultCrypto.DEV_DEFAULT_MASTER_KEY, "staging"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside dev profiles");
    }

    @Test
    void permitsRealMasterKeyUnderStagingProfile() {
        assertThatCode(() -> crypto("real-staging-master-key", "staging"))
                .doesNotThrowAnyException();
    }

    @Test
    void encryptDecryptRoundTripWithRealMasterKey() {
        VaultCrypto crypto = crypto("real-local-master-key");

        VaultCrypto.Encrypted encrypted = crypto.encrypt(
                "plain-secret", VaultCipher.AES_256_GCM, "real-local-master-key");

        assertThat(crypto.decrypt(
                encrypted.ciphertextBase64(), encrypted.nonceBase64(),
                VaultCipher.AES_256_GCM, "real-local-master-key"))
                .isEqualTo("plain-secret");
    }

    private static VaultCrypto crypto(String masterKey, String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return new VaultCrypto(new VaultProperties(masterKey), environment);
    }
}
