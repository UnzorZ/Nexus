package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.OidcFederationProperties;
import dev.unzor.nexus.identity.domain.exception.OidcFederationException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates and consumes the single-use {@code state} and {@code nonce} of a Google login.
 * Both are random 256-bit values kept in the HTTP session. {@link #consume} validates the
 * inbound {@code state} against the ticket and removes the ticket, so a replayed callback
 * finds nothing to match and is rejected.
 */
@Component
public class GoogleStateNonceService {

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Duration ttl;

    public GoogleStateNonceService(OidcFederationProperties properties) {
        this.ttl = properties.stateTtl();
    }

    /** Issues a fresh state+nonce, stores it in the session, and returns the ticket. */
    public OidcLoginState issue(UUID projectId, String continueUrl, HttpSession session) {
        Instant now = Instant.now();
        OidcLoginState ticket = new OidcLoginState(
                randomToken(GoogleOidc.STATE_BYTES),
                randomToken(GoogleOidc.NONCE_BYTES),
                projectId,
                continueUrl,
                now,
                now.plus(ttl));
        session.setAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY, ticket);
        return ticket;
    }

    /**
     * Validates the inbound {@code state} against the session ticket, then removes the
     * ticket unconditionally (single-use). Throws {@link OidcFederationException} on a
     * missing, expired, project-mismatched or unequal state.
     */
    public OidcLoginState consume(UUID projectId, String incomingState, HttpSession session) {
        Object raw = session.getAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY);
        // Remove first so a failed validation (or an exception afterwards) still invalidates
        // the ticket: a replay of the same callback can never succeed.
        session.removeAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY);
        if (!(raw instanceof OidcLoginState ticket)) {
            throw new OidcFederationException("invalid_state", "No pending Google login.");
        }
        Instant now = Instant.now();
        if (!Objects.equals(ticket.projectId(), projectId)
                || ticket.expiresAt().isBefore(now)
                || !constantTimeEquals(ticket.state(), incomingState)) {
            throw new OidcFederationException("invalid_state", "Google state is missing, expired or mismatched.");
        }
        return ticket;
    }

    /** Drops any pending ticket (for example when a login is abandoned). */
    public void clear(HttpSession session) {
        session.removeAttribute(GoogleOidc.LOGIN_STATE_SESSION_KEY);
    }

    private String randomToken(int byteLength) {
        byte[] buffer = new byte[byteLength];
        random.nextBytes(buffer);
        return urlEncoder.encodeToString(buffer);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
