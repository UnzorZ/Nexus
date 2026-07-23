package dev.unzor.nexus.identity.application.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.unzor.nexus.identity.domain.exception.OidcFederationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenVerifierTest {

    private static final String ISSUER = "https://accounts.google.com";
    private static final String ALTERNATE = "accounts.google.com";
    private static final String CLIENT_ID = "google-client-123";

    private KeyPair keyPair;
    private GoogleIdTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-kid")
                .build();
        // Decoder built from a JWKS containing the test key — mirrors the production
        // decoder, which verifies against Google's JWKS and matches the token kid.
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        verifier = new GoogleIdTokenVerifier(NimbusJwtDecoder.withJwkSource(jwkSource).build());
    }

    @Test
    void validTokenReturnsVerifiedClaims() throws Exception {
        String token = token(claims("google-sub-1", CLIENT_ID, "neo@example.com", true, "nonce-abc", ISSUER));

        VerifiedGoogleIdToken verified = verifier.verify(token, ISSUER, ALTERNATE, CLIENT_ID);

        assertThat(verified.subject()).isEqualTo("google-sub-1");
        assertThat(verified.email()).isEqualTo("neo@example.com");
        assertThat(verified.emailVerified()).isTrue();
        assertThat(verified.audience()).isEqualTo(CLIENT_ID);
        assertThat(verified.nonce()).isEqualTo("nonce-abc");
    }

    @Test
    void acceptsTheAlternateAccountsGoogleComIssuer() throws Exception {
        String token = token(claims("google-sub-1", CLIENT_ID, "neo@example.com", true, "n", ALTERNATE));

        VerifiedGoogleIdToken verified = verifier.verify(token, ISSUER, ALTERNATE, CLIENT_ID);

        assertThat(verified.issuer()).isEqualTo(ALTERNATE);
    }

    @Test
    void rejectsUnknownIssuer() throws Exception {
        String token = token(claims("google-sub-1", CLIENT_ID, "neo@example.com", true, "n", "https://evil.example.com"));

        assertThatThrownBy(() -> verifier.verify(token, ISSUER, ALTERNATE, CLIENT_ID))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("invalid_id_token");
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        String token = token(claims("google-sub-1", "another-client", "neo@example.com", true, "n", ISSUER));

        assertThatThrownBy(() -> verifier.verify(token, ISSUER, ALTERNATE, CLIENT_ID))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("invalid_id_token");
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        JWTClaimsSet expired = new JWTClaimsSet.Builder(claims("google-sub-1", CLIENT_ID, "neo@example.com", true, "n", ISSUER))
                .expirationTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .build();
        String token = token(expired);

        assertThatThrownBy(() -> verifier.verify(token, ISSUER, ALTERNATE, CLIENT_ID))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("invalid_id_token");
    }

    @Test
    void rejectsUnverifiedEmail() throws Exception {
        String token = token(claims("google-sub-1", CLIENT_ID, "neo@example.com", false, "n", ISSUER));

        assertThatThrownBy(() -> verifier.verify(token, ISSUER, ALTERNATE, CLIENT_ID))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("email_not_verified");
    }

    @Test
    void rejectsMissingEmail() throws Exception {
        JWTClaimsSet noEmail = new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject("google-sub-1").audience(CLIENT_ID)
                .expirationTime(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
                .claim("email_verified", true).claim("nonce", "n").build();
        String token = token(noEmail);

        assertThatThrownBy(() -> verifier.verify(token, ISSUER, ALTERNATE, CLIENT_ID))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("email_missing");
    }

    @Test
    void rejectsTokenSignedByAnotherKey() throws Exception {
        // A second key pair signs the token; the verifier trusts the original key only.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair attacker = generator.generateKeyPair();
        String token = token(claims("google-sub-1", CLIENT_ID, "neo@example.com", true, "n", ISSUER),
                (RSAPrivateKey) attacker.getPrivate());

        assertThatThrownBy(() -> verifier.verify(token, ISSUER, ALTERNATE, CLIENT_ID))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("invalid_id_token");
    }

    private JWTClaimsSet claims(String subject, String audience, String email, boolean emailVerified, String nonce, String issuer) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .expirationTime(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
                .issueTime(Date.from(Instant.now()))
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("nonce", nonce)
                .claim("name", "Neo")
                .build();
    }

    private String token(JWTClaimsSet claims) throws Exception {
        return token(claims, (RSAPrivateKey) keyPair.getPrivate());
    }

    private String token(JWTClaimsSet claims, RSAPrivateKey signer) throws Exception {
        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(), claims);
        signed.sign(new RSASSASigner(signer));
        return signed.serialize();
    }
}
