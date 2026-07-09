package io.nexus.client.security;

import io.nexus.client.NexusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NexusBackChannelLogoutControllerTest {

    private static final String ISS = "http://localhost:8080/p/demo";
    private static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final NexusProperties properties = new NexusProperties();
    private final NexusBackChannelLogoutController controller =
            new NexusBackChannelLogoutController(decoder, properties, publisher);

    @BeforeEach
    void configureIssuer() {
        properties.getSecurity().setIssuer(ISS);
    }

    @Test
    void validTokenPublishesEventAndReturns200() {
        when(decoder.decode("jwt")).thenReturn(buildJwt(Map.of("events", Map.of(EVENT, Map.of())), false, false));

        ResponseEntity<Void> response = controller.backchannel("jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(publisher).publishEvent(any(NexusBackChannelLogoutEvent.class));
    }

    @Test
    void rejectsTokenWithNonce() {
        when(decoder.decode("jwt")).thenReturn(buildJwt(Map.of("events", Map.of(EVENT, Map.of())), false, true));

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void rejectsTokenWithoutBackchannelEvent() {
        when(decoder.decode("jwt")).thenReturn(buildJwt(Map.of("events", Map.of("other", Map.of())), false, false));

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void rejectsExpiredToken() {
        when(decoder.decode("jwt")).thenReturn(buildJwt(Map.of("events", Map.of(EVENT, Map.of())), true, false));

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void rejectsUndecodableToken() {
        when(decoder.decode("jwt")).thenThrow(new RuntimeException("bad signature"));

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    private Jwt buildJwt(Map<String, Object> extraClaims, boolean expired, boolean withNonce) {
        var builder = Jwt.withTokenValue("jwt").header("alg", "RS256")
                .issuer(ISS)
                .subject("alice")
                .issuedAt(Instant.now().minusSeconds(70))
                .expiresAt(expired ? Instant.now().minusSeconds(10) : Instant.now().plusSeconds(60))
                .claim("jti", "jti-" + System.nanoTime());
        if (withNonce) {
            builder.claim("nonce", "should-not-be-here");
        }
        extraClaims.forEach(builder::claim);
        return builder.build();
    }
}
