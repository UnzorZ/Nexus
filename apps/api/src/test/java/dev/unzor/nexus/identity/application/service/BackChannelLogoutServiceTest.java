package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests del fan-out + retry del back-channel logout (RFC 8417 §2.7). El cliente HTTP se
 * liga a un {@link MockRestServiceServer} para driving las respuestas del RP y verificar el
 * número exacto de intentos.
 */
class BackChannelLogoutServiceTest {

    private static final String BACKCHANNEL_URI = "https://rp.example.com/backchannel-logout";

    private final BackChannelLogoutClientResolver resolver = mock(BackChannelLogoutClientResolver.class);
    private final BackChannelLogoutTokenIssuer tokenIssuer = mock(BackChannelLogoutTokenIssuer.class);

    @Test
    void retriesTransientFailuresThenSucceeds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BackChannelLogoutService service = newService(builder.build());

        when(resolver.resolve(anyString(), any())).thenReturn(List.of(client()));
        when(tokenIssuer.issue(anyString(), anyString())).thenReturn(jwt());

        // 500, 504, luego 200: el servicio reintenta los fallos transitorios y entrega.
        server.expect(requestTo(BACKCHANNEL_URI)).andRespond(withServerError());
        server.expect(requestTo(BACKCHANNEL_URI)).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        server.expect(requestTo(BACKCHANNEL_URI)).andRespond(withSuccess());

        service.onLogoutRequested(event());

        server.verify(); // exactamente 3 peticiones
    }

    @Test
    void doesNotRetryOn4xxRejection() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BackChannelLogoutService service = newService(builder.build());

        when(resolver.resolve(anyString(), any())).thenReturn(List.of(client()));
        when(tokenIssuer.issue(anyString(), anyString())).thenReturn(jwt());

        // Un 4xx es un rechazo definitivo del RP (token inválido para él): no se reintenta.
        server.expect(requestTo(BACKCHANNEL_URI)).andRespond(withBadRequest());

        service.onLogoutRequested(event());

        server.verify(); // exactamente 1 petición
    }

    @Test
    void givesUpAfterMaxAttemptsWhenAllTransient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BackChannelLogoutService service = newService(builder.build());

        when(resolver.resolve(anyString(), any())).thenReturn(List.of(client()));
        when(tokenIssuer.issue(anyString(), anyString())).thenReturn(jwt());

        // 3 fallos transitorios seguidos: agota MAX_ATTEMPTS y cede (sin lanzar; el RP
        // recuperará consistencia al re-emitir en su próximo login).
        server.expect(requestTo(BACKCHANNEL_URI)).andRespond(withServerError());
        server.expect(requestTo(BACKCHANNEL_URI)).andRespond(withServerError());
        server.expect(requestTo(BACKCHANNEL_URI)).andRespond(withServerError());

        service.onLogoutRequested(event());

        server.verify(); // exactamente MAX_ATTEMPTS peticiones
    }

    private BackChannelLogoutService newService(RestClient client) {
        return new BackChannelLogoutService(resolver, tokenIssuer, client);
    }

    private static ProjectOauthClient client() {
        ProjectOauthClient c = new ProjectOauthClient(
                UUID.randomUUID(), "nxo-x", "hash", "Web",
                List.of("https://rp.example.com/callback"), List.of(),
                List.of("authorization_code"), List.of("openid"),
                true, false, null);
        c.updateBackchannelLogoutUri(BACKCHANNEL_URI);
        return c;
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("dummy-logout-token")
                .header("alg", "RS256")
                .claim("sub", "alice")
                .build();
    }

    private static BackChannelLogoutRequested event() {
        return new BackChannelLogoutRequested("alice", UUID.randomUUID(), "https://nexus/p/smoke");
    }
}
