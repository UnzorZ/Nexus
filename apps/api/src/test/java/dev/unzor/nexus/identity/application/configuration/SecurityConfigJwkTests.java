package dev.unzor.nexus.identity.application.configuration;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la carga de la clave activa desde un keystore PKCS12 y el ciclo completo
 * de firma + validación de un token.
 *
 * <p>Usa un keystore de test compromido con dos alias ({@code old}, {@code new}):
 * {@code src/test/resources/keystore/test-two-keys.p12}. Solo se carga el alias
 * activo, porque el {@code NimbusJwtEncoder} del framework no soporta varias claves
 * simultáneas (ver ADR-0011).</p>
 */
class SecurityConfigJwkTests {

    private static final String KEYSTORE = "keystore/test-two-keys.p12";
    private static final String PASSWORD = "storepass";

    @Test
    void loadsTheKeyForTheRequestedAlias() {
        RSAKey newKey = SecurityConfig.loadKeyFromKeystore(
                new ClassPathResource(KEYSTORE), PASSWORD, "new", PASSWORD);
        RSAKey oldKey = SecurityConfig.loadKeyFromKeystore(
                new ClassPathResource(KEYSTORE), PASSWORD, "old", PASSWORD);

        assertThat(newKey.getKeyID()).isNotBlank();
        assertThat(oldKey.getKeyID()).isNotBlank();
        // Cada alias resuelve a su propia clave (kids distintos).
        assertThat(newKey.getKeyID()).isNotEqualTo(oldKey.getKeyID());
        // El kid es determinista: dos cargas del mismo alias dan el mismo valor.
        RSAKey newKeyAgain = SecurityConfig.loadKeyFromKeystore(
                new ClassPathResource(KEYSTORE), PASSWORD, "new", PASSWORD);
        assertThat(newKeyAgain.getKeyID()).isEqualTo(newKey.getKeyID());
    }

    @Test
    void signsAndValidatesATokenWithTheActiveKey() {
        RSAKey key = SecurityConfig.loadKeyFromKeystore(
                new ClassPathResource(KEYSTORE), PASSWORD, "new", PASSWORD);
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(key));
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(source);
        JwtDecoder decoder = OAuth2AuthorizationServerConfiguration.jwtDecoder(source);

        Jwt token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder()
                        .subject("signing-test")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build()));

        assertThat(token.getHeaders().get("kid")).isEqualTo(key.getKeyID());

        Jwt decoded = decoder.decode(token.getTokenValue());
        assertThat(decoded.getSubject()).isEqualTo("signing-test");
    }
}
