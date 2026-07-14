package dev.unzor.nexus.sdk.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que el decoder JWT valida el {@code iss} contra el issuer configurado.
 * Todos los realms comparten la clave de firma, así que sin validación de iss un
 * token válido de OTRO proyecto autenticaría. Replicamos la configuración del
 * bean {@code nexusJwtDecoder} (decoder + JwtValidators.createDefaultWithIssuer).
 */
class NexusJwtDecoderIssTest {

    private static final String ISSUER = "http://localhost:8080/p/demo";

    @Test
    void acceptsTokenWithConfiguredIssuer() throws Exception {
        RSAKey key = key();
        NimbusJwtDecoder decoder = decoder(key);
        String token = encode(key, ISSUER);

        assertThat(decoder.decode(token).getClaimAsString("iss")).isEqualTo(ISSUER);
    }

    @Test
    void rejectsTokenFromAnotherRealm() throws Exception {
        RSAKey key = key();
        NimbusJwtDecoder decoder = decoder(key);
        // Mismo key de firma (realms lo comparten), pero issuer de OTRO realm.
        String token = encode(key, "http://localhost:8080/p/other-project");

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(BadJwtException.class);
    }

    private static NimbusJwtDecoder decoder(RSAKey key) {
        NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSource(jwkSource(key)).build();
        d.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return d;
    }

    private static JWKSource<SecurityContext> jwkSource(RSAKey key) {
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    private static RSAKey key() throws Exception {
        return new RSAKeyGenerator(2048).keyID("test-key").generate();
    }

    private static String encode(RSAKey key, String issuer) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("alice")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
