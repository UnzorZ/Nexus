package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.exception.OidcFederationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;

/**
 * Verifies a Google id_token end to end: signature (and expiry, via the injected
 * {@link JwtDecoder} backed by Google's JWKS), issuer, audience, subject presence and
 * email verification. The decoder is injected so tests can supply one backed by a test key.
 *
 * <p>The {@code iss} claim is accepted if it equals either of Google's two documented
 * values ({@code https://accounts.google.com} or {@code accounts.google.com}).</p>
 */
public class GoogleIdTokenVerifier {

    private final JwtDecoder decoder;

    public GoogleIdTokenVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    public VerifiedGoogleIdToken verify(
            String idToken, String expectedIssuer, String alternateIssuer, String expectedClientId) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException exception) {
            // Bad signature, malformed JWT, or a timestamp failure (exp/nbf) from the decoder.
            throw new OidcFederationException("invalid_id_token", "The Google id_token could not be verified.", exception);
        }

        String issuer = jwt.getClaimAsString("iss");
        if (!expectedIssuer.equals(issuer) && !alternateIssuer.equals(issuer)) {
            throw new OidcFederationException("invalid_id_token", "The Google id_token issuer does not match.");
        }

        List<String> audiences = jwt.getAudience();
        if (audiences == null || audiences.stream().noneMatch(expectedClientId::equals)) {
            throw new OidcFederationException("invalid_id_token", "The Google id_token audience does not match.");
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw new OidcFederationException("invalid_id_token", "The Google id_token is expired.");
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new OidcFederationException("invalid_id_token", "The Google id_token has no subject.");
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new OidcFederationException("email_missing", "The Google id_token has no email claim.");
        }
        if (!isEmailVerified(jwt)) {
            throw new OidcFederationException("email_not_verified", "The Google email is not verified.");
        }

        return new VerifiedGoogleIdToken(
                subject,
                email,
                true,
                issuer,
                expectedClientId,
                jwt.getClaimAsString("nonce"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"));
    }

    private static boolean isEmailVerified(Jwt jwt) {
        Object raw = jwt.getClaim("email_verified");
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }
}
