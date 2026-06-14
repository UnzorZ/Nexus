package dev.unzor.nexus.admin.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanelContinueUrlValidatorTests {

    private final PanelContinueUrlValidator validator =
            new PanelContinueUrlValidator("http://localhost:3000");

    @Test
    void acceptsContinueUrlOnConfiguredFrontend() {
        assertThat(validator.isAllowed("http://localhost:3000/dashboard")).isTrue();
    }

    @Test
    void rejectsExternalContinueUrl() {
        assertThat(validator.isAllowed("https://evil.example/phish")).isFalse();
    }

    @Test
    void defaultDashboardUsesFrontendBaseUrl() {
        assertThat(validator.defaultDashboardUrl()).isEqualTo("http://localhost:3000/dashboard");
    }
}
