package dev.unzor.nexus.notify.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotifyCryptoTests {

    @Test
    void permitsDevDefaultUnderDefaultProfile() {
        assertThatCode(() -> crypto(NotifyCrypto.DEV_DEFAULT_MASTER_KEY))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsDevDefaultUnderKnownDevProfile() {
        assertThatCode(() -> crypto(NotifyCrypto.DEV_DEFAULT_MASTER_KEY, "remote-dev"))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesDevDefaultUnderStagingProfile() {
        assertThatThrownBy(() -> crypto(NotifyCrypto.DEV_DEFAULT_MASTER_KEY, "staging"))
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
        NotifyCrypto crypto = crypto("real-local-master-key");

        String encrypted = crypto.encrypt("smtp-secret");

        assertThat(crypto.decrypt(encrypted)).isEqualTo("smtp-secret");
    }

    private static NotifyCrypto crypto(String masterKey, String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return new NotifyCrypto(masterKey, environment);
    }
}
