package dev.unzor.nexus.identity.application.service;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BackChannelLogoutTokenIssuerTest {

    private final BackChannelLogoutTokenIssuer issuer = new BackChannelLogoutTokenIssuer(testJwkSource());

    @Test
    void issuesSignedLogoutTokenWithRequiredRfc8417Claims() {
        Jwt token = issuer.issue("http://localhost/p/realm", "alice");

        // Claims exigidos por RFC 8417 §2.4.
        Map<String, Object> claims = token.getClaims();
        assertThat(claims).containsKeys("iss", "sub", "iat", "exp", "jti", "events");
        // iss se tipa como java.net.URL (el encoder lo parsea); en el wire (JSON) sale como
        // string, así que el RP lo recibe bien. Comparamos por toString.
        assertThat(claims.get("iss").toString()).isEqualTo("http://localhost/p/realm");
        assertThat(claims.get("sub").toString()).isEqualTo("alice");

        // events = {"http://schemas.openid.net/event/backchannel-logout": {}}
        @SuppressWarnings("unchecked")
        Map<String, Object> events = (Map<String, Object>) claims.get("events");
        assertThat(events).containsKey(BackChannelLogoutTokenIssuer.BACKCHANNEL_LOGOUT_EVENT);

        // Firmado con la clave del AS (RS256), mismo JWKS que los access/ID tokens. El header
        // alg se tipa como SignatureAlgorithm (enum); comparamos por toString.
        assertThat(token.getHeaders().get("alg").toString()).startsWith("RS256");
        assertThat(token.getHeaders()).containsKey("kid");

        // Sin aud, sin nonce (RFC 8417 lo prohíbe).
        assertThat(claims).doesNotContainKeys("aud", "nonce");
    }

    private static JWKSource<SecurityContext> testJwkSource() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
                    .privateKey((RSAPrivateKey) kp.getPrivate())
                    .keyID("test-kid")
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
