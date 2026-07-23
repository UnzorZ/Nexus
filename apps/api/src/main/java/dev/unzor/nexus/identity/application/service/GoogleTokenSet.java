package dev.unzor.nexus.identity.application.service;

/**
 * Tokens returned by Google's token endpoint for an authorization code. Only the
 * {@code idToken} is required for login; {@code accessToken} is kept for completeness
 * (the id_token already carries the identity claims Nexus needs).
 */
public record GoogleTokenSet(
        String idToken,
        String accessToken,
        String tokenType,
        String scope
) {
}
