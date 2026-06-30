package dev.unzor.nexus.apikeys.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Emite y resuelve instance tokens opacos (ADR-0012) respaldados en Redis (el
 * store compartido y revocable de ADR-0008). Un token es una credencial efímera
 * (TTL 1h por defecto) ligada a una API key resuelta, que las apps usan en
 * latidos de alta frecuencia en lugar de la key larga: su verificación es un
 * {@code GET} de Redis, sin SHA-256 ni escritura de {@code last_used_at} por
 * beat. El token identifica al proyecto (mismo contrato que {@link ResolvedApiKey});
 * el binding por instancia queda como endurecimiento futuro.
 */
@Component
public class InstanceTokenService {

    /** TTL por defecto de un instance token. */
    public static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private static final String KEY_PREFIX = "nexus:apikeys:itok:";
    private static final int TOKEN_BYTES = 32;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl = DEFAULT_TTL;

    public InstanceTokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Emite un token para la key resuelta y lo guarda en Redis con el TTL. */
    public Issued mint(ResolvedApiKey key) {
        String token = newToken();
        InstanceTokenPayload payload = new InstanceTokenPayload(
                key.projectId(), key.keyId(), key.keyPrefix(), key.scopes(), Instant.now().plus(ttl));
        try {
            redis.opsForValue().set(KEY_PREFIX + token, MAPPER.writeValueAsString(payload), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo emitir el instance token", e);
        }
        return new Issued(token, ttl.toSeconds());
    }

    /**
     * Resuelve un token a su key resuelta, o vacío si no existe / expiró / está
     * corrupto (Redis aplica el TTL, así que la expiración es implícita).
     */
    public Optional<ResolvedApiKey> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String json = redis.opsForValue().get(KEY_PREFIX + token);
        if (json == null) {
            return Optional.empty();
        }
        try {
            InstanceTokenPayload payload = MAPPER.readValue(json, InstanceTokenPayload.class);
            return Optional.of(new ResolvedApiKey(
                    payload.projectId(), payload.keyId(), payload.keyPrefix(), payload.scopes()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Duration ttl() {
        return ttl;
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Resultado de emitir un token: el valor opaco y los segundos hasta expirar. */
    public record Issued(String token, long expiresInSeconds) {
    }
}
