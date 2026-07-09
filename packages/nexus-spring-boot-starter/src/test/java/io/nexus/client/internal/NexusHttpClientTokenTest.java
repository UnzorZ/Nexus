package io.nexus.client.internal;

import io.nexus.client.NexusProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la gestión del instance token en {@link NexusHttpClient}: el scheduler
 * (P1-3) re-registra cuando deja de ser válido y, hasta entonces, el interceptor
 * cae a la API key cruda. Aquí cubrimos la validez/rotación que orquesta el scheduler.
 */
class NexusHttpClientTokenTest {

    private final NexusHttpClient client = new NexusHttpClient(props());

    @Test
    void noTokenByDefault() {
        assertThat(client.instanceTokenValid()).isFalse();
    }

    @Test
    void validTokenWhileNotExpired() {
        client.useInstanceToken("tok", Instant.now().plusSeconds(60));
        assertThat(client.instanceTokenValid()).isTrue();
    }

    @Test
    void expiredTokenIsInvalid() {
        client.useInstanceToken("tok", Instant.now().minusSeconds(1));
        assertThat(client.instanceTokenValid()).isFalse();
    }

    @Test
    void clearForcesRawKey() {
        client.useInstanceToken("tok", Instant.now().plusSeconds(60));
        assertThat(client.instanceTokenValid()).isTrue();
        client.clearInstanceToken();
        assertThat(client.instanceTokenValid()).isFalse();
    }

    @Test
    void blankTokenIsIgnored() {
        client.useInstanceToken("  ", Instant.now().plusSeconds(60));
        assertThat(client.instanceTokenValid()).isFalse();
    }

    private static NexusProperties props() {
        NexusProperties p = new NexusProperties();
        p.setUrl("http://localhost:8080");
        p.setApiKey("nxs-test");
        p.setAppName("test-app");
        return p;
    }
}
