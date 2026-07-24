package dev.unzor.nexus.identity.application.service;

/**
 * Claims of a Google id_token after signature, issuer, audience and expiry were verified.
 * The {@code nonce} is carried so the orchestrator can confirm it matches the value sent
 * in the authorization request (replay protection).
 */
public record VerifiedGoogleIdToken(
        String subject,
        String email,
        boolean emailVerified,
        String issuer,
        String audience,
        String nonce,
        String name,
        String givenName,
        String familyName
) {
}
