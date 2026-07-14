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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NexusBackChannelLogoutControllerTest {

    private static final String ISS = "http://localhost:8080/p/demo";
    private static final String CLIENT_ID = "demo-client";
    private static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final NexusProperties properties = new NexusProperties();
    private final NexusBackChannelLogoutController controller =
            new NexusBackChannelLogoutController(decoder, properties, publisher);

    @BeforeEach
    void configureIssuer() {
        properties.getSecurity().setIssuer(ISS);
        properties.getSecurity().getClient().setClientId(CLIENT_ID);
    }

    @Test
    void validTokenPublishesEventAndReturns200() {
        when(decoder.decode("jwt")).thenReturn(validBuilder().build());

        ResponseEntity<Void> response = controller.backchannel("jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(publisher).publishEvent(any(NexusBackChannelLogoutEvent.class));
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        // aud emitido para otro RP del mismo realm: la clave de firma es compartida,
        // así que aud es lo que distingue a qué cliente va dirigido el logout.
        when(decoder.decode("jwt")).thenReturn(
                minimalBuilder().audience(List.of("other-client")).build());

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void rejectsTokenWithoutAudience() {
        when(decoder.decode("jwt")).thenReturn(minimalBuilder().build());

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void rejectsTokenWithoutIat() {
        when(decoder.decode("jwt")).thenReturn(
                minimalBuilder().audience(List.of(CLIENT_ID)).claim("jti", "jti-1").build());

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void rejectsTokenWithoutJti() {
        when(decoder.decode("jwt")).thenReturn(
                minimalBuilder().audience(List.of(CLIENT_ID)).issuedAt(Instant.now().minusSeconds(70)).build());

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void rejectsTokenWithNonce() {
        when(decoder.decode("jwt")).thenReturn(validBuilder().claim("nonce", "should-not-be-here").build());

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void rejectsTokenWithoutBackchannelEvent() {
        when(decoder.decode("jwt")).thenReturn(validBuilder().claim("events", Map.of("other", Map.of())).build());

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void rejectsExpiredToken() {
        when(decoder.decode("jwt")).thenReturn(validBuilder().expiresAt(Instant.now().minusSeconds(10)).build());

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void rejectsUndecodableToken() {
        when(decoder.decode("jwt")).thenThrow(new RuntimeException("bad signature"));

        assertThat(controller.backchannel("jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publishEvent(any());
    }

    /** Builder con todos los claims spec-required presentes y válidos (aud, iat, jti, events). */
    private Jwt.Builder validBuilder() {
        return Jwt.withTokenValue("jwt").header("alg", "RS256")
                .issuer(ISS)
                .subject("alice")
                .audience(List.of(CLIENT_ID))
                .issuedAt(Instant.now().minusSeconds(70))
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("jti", "jti-" + System.nanoTime())
                .claim("events", Map.of(EVENT, Map.of()));
    }

    /**
     * Builder mínimo (iss, sub, exp, events) sin aud/iat/jti. Cada test de "claim ausente"
     * añade solo los demás, para aislar exactamente el claim que falta sin depender de la
     * semántica overwrite/no-op de {@link Jwt.Builder}.
     */
    private Jwt.Builder minimalBuilder() {
        return Jwt.withTokenValue("jwt").header("alg", "RS256")
                .issuer(ISS)
                .subject("alice")
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("events", Map.of(EVENT, Map.of()));
    }
}
