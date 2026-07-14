package dev.unzor.nexus.identity.application.service;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emite un <b>back-channel logout token</b> (OIDC Back-Channel Logout 1.0): un JWT firmado con la clave de
 * firma del Authorization Server (mismo {@link JWKSource} que los access/ID tokens, para que
 * el RP lo valide contra el JWKS) con los claims exigidos por la spec:
 * <ul>
 *   <li>{@code iss} — el issuer del realm ({@code {origin}/p/{slug}}).</li>
 *   <li>{@code sub} — el subject del usuario (= el {@code sub} del id_token, aquí
 *       {@code ProjectUserPrincipal.getName()}).</li>
 *   <li>{@code aud} — el {@code client_id} del RP al que va dirigido (spec §2.4 SHOULD;
 *       el RP lo valida para descartar tokens emitidos para otro cliente).</li>
 *   <li>{@code iat}, {@code exp} (corto), {@code jti} (único).</li>
 *   <li>{@code events} = {@code {"http://schemas.openid.net/event/backchannel-logout": {}}}.</li>
 * </ul>
 * Sin {@code nonce} (spec §2.4: un logout token NO debe llevarlo).
 */
@Component
public class BackChannelLogoutTokenIssuer {

    public static final String BACKCHANNEL_LOGOUT_EVENT =
            "http://schemas.openid.net/event/backchannel-logout";

    private final NimbusJwtEncoder encoder;

    public BackChannelLogoutTokenIssuer(JWKSource<SecurityContext> jwkSource) {
        this.encoder = new NimbusJwtEncoder(jwkSource);
    }

    public Jwt issue(String issuer, String subject, String audienceClientId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .audience(List.of(audienceClientId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(120))
                .id(UUID.randomUUID().toString())
                .claim("events", Map.of(BACKCHANNEL_LOGOUT_EVENT, Map.of()))
                .build();
        // JwtEncoderParameters.from(claims) usa el algoritmo por defecto del encoder (RS256
        // para la clave RSA activa) y fija el kid desde el JWKS — mismo que los access/ID
        // tokens, para que el RP valide la firma contra el mismo JWKS.
        return encoder.encode(JwtEncoderParameters.from(claims));
    }
}
