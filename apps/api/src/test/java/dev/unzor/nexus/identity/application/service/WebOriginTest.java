package dev.unzor.nexus.identity.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebOriginTest {

    @Test
    void derivesOriginFromRedirectUri() {
        assertThat(WebOrigin.of("https://flip.unzor.dev/login/oauth2/code/nexus"))
                .isEqualTo("https://flip.unzor.dev");
    }

    @Test
    void keepsNonDefaultPort() {
        assertThat(WebOrigin.of("http://localhost:8080/cb")).isEqualTo("http://localhost:8080");
        assertThat(WebOrigin.of("https://app.example:8443/cb")).isEqualTo("https://app.example:8443");
    }

    @Test
    void dropsDefaultPort() {
        assertThat(WebOrigin.of("https://app.example:443/cb")).isEqualTo("https://app.example");
        assertThat(WebOrigin.of("http://app.example:80/cb")).isEqualTo("http://app.example");
    }

    @Test
    void lowercasesSchemeAndHost() {
        assertThat(WebOrigin.of("HTTPS://App.Example/CB")).isEqualTo("https://app.example");
    }

    @Test
    void returnsNullForInvalidOrRelativeOrHostless() {
        assertThat(WebOrigin.of(null)).isNull();
        assertThat(WebOrigin.of("")).isNull();
        assertThat(WebOrigin.of("   ")).isNull();
        assertThat(WebOrigin.of("not a url")).isNull();
        assertThat(WebOrigin.of("/relative/path")).isNull();
        assertThat(WebOrigin.of("mailto:foo@bar.com")).isNull();
    }
}
