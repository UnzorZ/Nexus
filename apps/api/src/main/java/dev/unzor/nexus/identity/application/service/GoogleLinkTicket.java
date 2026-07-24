package dev.unzor.nexus.identity.application.service;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Pending account-linking ticket, stored in the HTTP session when a Google identity is
 * verified but is not yet linked to a project user whose email matches. Linking requires
 * re-authentication: this ticket is created only after Google proved the identity, and is
 * consumed by {@code completeLinking} only after the user proves ownership of the existing
 * account with its password. It carries the original {@code continueUrl} so a successful
 * link can resume the authorization flow that started the login.
 *
 * <p>Serializable because Spring Session Redis serializes session attributes with JDK
 * serialization.</p>
 */
public record GoogleLinkTicket(
        UUID projectId,
        UUID projectUserId,
        String provider,
        String subject,
        String email,
        String displayName,
        String continueUrl,
        Instant authenticatedAt,
        Instant expiresAt
) implements Serializable {
}
