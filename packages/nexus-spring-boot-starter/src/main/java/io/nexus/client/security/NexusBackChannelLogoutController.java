package io.nexus.client.security;

import io.nexus.client.NexusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Endpoint de <b>back-channel logout</b> (OIDC RFC 8417). Nexus POSTea aquí
 * {@code logout_token=<jwt>} (form) cuando la sesión de un usuario termina; el
 * controlador valida el token y publica {@link NexusBackChannelLogoutEvent} para
 * que la app invalide la sesión local del {@code sub}.
 *
 * <p>Validación (RFC 8417 §2.4): firma RS256 contra el JWKS del realm (vía
 * {@link JwtDecoder}), {@code iss}, {@code exp}, presencia del claim {@code events}
 * con el evento de back-channel-logout, <b>ausencia</b> de {@code nonce}, y
 * dedupe de {@code jti} (anti-replay). Responde 200 si el token es válido; los
 * fallos de validación responden 400 (Nexus no reintenta 4xx — entrega
 * best-effort).</p>
 */
@RestController
public class NexusBackChannelLogoutController {

    private static final Logger log = LoggerFactory.getLogger(NexusBackChannelLogoutController.class);
    private static final String EVENT_URI = "http://schemas.openid.net/event/backchannel-logout";
    private static final Duration JTI_TTL = Duration.ofMinutes(15);

    private final JwtDecoder jwtDecoder;
    private final NexusProperties properties;
    private final ApplicationEventPublisher publisher;
    private final ConcurrentHashMap<String, Instant> seenJtis = new ConcurrentHashMap<>();

    public NexusBackChannelLogoutController(JwtDecoder jwtDecoder, NexusProperties properties,
                                            ApplicationEventPublisher publisher) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
        this.publisher = publisher;
    }

    @PostMapping(path = "${nexus.security.backchannel-logout-path:/logout/backchannel}")
    public ResponseEntity<Void> backchannel(@RequestParam("logout_token") String token) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (RuntimeException e) {
            log.warn("Back-channel logout token inválido (firma/decoding): {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        String iss = properties.getSecurity().getIssuer();
        String tokenIss = jwt.getClaimAsString("iss"); // iss puede estar tipado como URL; normalizamos
        if (iss != null && !iss.equals(tokenIss)) {
            log.warn("Back-channel logout iss inesperado: {}", tokenIss);
            return ResponseEntity.badRequest().build();
        }
        if (jwt.getExpiresAt() == null || jwt.getExpiresAt().isBefore(Instant.now())) {
            return ResponseEntity.badRequest().build();
        }
        if (jwt.getClaimAsString("nonce") != null) {
            // RFC 8417 §2.4: un logout token NO debe llevar nonce.
            return ResponseEntity.badRequest().build();
        }
        Object events = jwt.getClaims().get("events");
        if (!(events instanceof Map<?, ?> eventMap) || !eventMap.containsKey(EVENT_URI)) {
            return ResponseEntity.badRequest().build();
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String jti = jwt.getId();
        if (jti != null && seenJtis.putIfAbsent(jti, Instant.now()) != null) {
            // Replay del mismo jti — idempotente: respondemos 200 sin re-publicar.
            log.debug("Back-channel logout jti duplicado ignorado: {}", jti);
            return ResponseEntity.ok().build();
        }
        purgeOldJtis();

        log.info("Back-channel logout válido para sub={} (iss={})", sub, tokenIss);
        publisher.publishEvent(new NexusBackChannelLogoutEvent(sub, tokenIss));
        return ResponseEntity.ok().build();
    }

    private void purgeOldJtis() {
        Instant cutoff = Instant.now().minus(JTI_TTL);
        seenJtis.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }
}
