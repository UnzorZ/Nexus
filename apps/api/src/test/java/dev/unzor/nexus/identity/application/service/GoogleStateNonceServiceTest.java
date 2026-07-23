package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.OidcFederationProperties;
import dev.unzor.nexus.identity.domain.exception.OidcFederationException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleStateNonceServiceTest {

    private final HttpSession session = mock(HttpSession.class);
    private GoogleStateNonceService service;

    @BeforeEach
    void setUp() {
        // null google triggers the compact-constructor defaults; state TTL is 5 minutes.
        service = new GoogleStateNonceService(new OidcFederationProperties(null, Duration.ofMinutes(5), Duration.ofMinutes(10)));
    }

    @Test
    void issueStoresTicketInSession() {
        UUID projectId = UUID.randomUUID();

        OidcLoginState ticket = service.issue(projectId, "/continue", session);

        assertThat(ticket.state()).isNotBlank();
        assertThat(ticket.nonce()).isNotBlank();
        assertThat(ticket.state()).isNotEqualTo(ticket.nonce());
        verify(session).setAttribute(eq(GoogleOidc.LOGIN_STATE_SESSION_KEY), eq(ticket));
    }

    @Test
    void consumeAcceptsMatchingStateAndRemovesTicket() {
        UUID projectId = UUID.randomUUID();
        OidcLoginState ticket = service.issue(projectId, null, session);
        when(session.getAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY)).thenReturn(ticket);

        OidcLoginState consumed = service.consume(projectId, ticket.state(), session);

        assertThat(consumed.state()).isEqualTo(ticket.state());
        verify(session).removeAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY);
    }

    @Test
    void consumeRejectsMismatchedState() {
        UUID projectId = UUID.randomUUID();
        OidcLoginState ticket = service.issue(projectId, null, session);
        when(session.getAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY)).thenReturn(ticket);

        assertThatThrownBy(() -> service.consume(projectId, "tampered-state", session))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("invalid_state");
        // Ticket is removed even on failure so the same value cannot be retried.
        verify(session).removeAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY);
    }

    @Test
    void consumedStateCannotBeReplayed() {
        UUID projectId = UUID.randomUUID();
        service.issue(projectId, null, session);
        // After the first consume removed the attribute, a replay finds nothing.
        when(session.getAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY)).thenReturn(null);

        assertThatThrownBy(() -> service.consume(projectId, "anything", session))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("invalid_state");
    }

    @Test
    void consumeRejectsExpiredTicket() {
        UUID projectId = UUID.randomUUID();
        OidcLoginState expired = new OidcLoginState(
                "state", "nonce", projectId, null, Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        when(session.getAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY)).thenReturn(expired);

        assertThatThrownBy(() -> service.consume(projectId, "state", session))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("invalid_state");
    }

    @Test
    void consumeRejectsTicketIssuedForAnotherProject() {
        UUID projectId = UUID.randomUUID();
        OidcLoginState otherRealm = new OidcLoginState(
                "state", "nonce", UUID.randomUUID(), null, Instant.now(), Instant.now().plusSeconds(60));
        when(session.getAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY)).thenReturn(otherRealm);

        assertThatThrownBy(() -> service.consume(projectId, "state", session))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("invalid_state");
    }
}
