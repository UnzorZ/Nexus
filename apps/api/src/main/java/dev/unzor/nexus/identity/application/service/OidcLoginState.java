package dev.unzor.nexus.identity.application.service;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Single-use state and nonce of an in-flight Google login, stored in the HTTP session.
 * {@code state} is returned by Google on the callback and matched back; {@code nonce} is
 * sent to Google and must come back inside the id_token. The ticket is consumed (removed
 * from the session) the first time it is validated, which makes both values unreplayable.
 *
 * <p>Serializable because Spring Session Redis serializes session attributes with JDK
 * serialization.</p>
 */
public record OidcLoginState(
        String state,
        String nonce,
        UUID projectId,
        String continueUrl,
        Instant createdAt,
        Instant expiresAt
) implements Serializable {
}
