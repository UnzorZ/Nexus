package dev.unzor.nexus.apikeys.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstanceTokenServiceTests {

    // Fake en memoria del store de Redis (values + índice inverso keyId→tokens).
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Set<String>> sets = new HashMap<>();
    private StringRedisTemplate redis;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUpFakeRedis() {
        values.clear();
        sets.clear();
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> vops = mock(ValueOperations.class);
        SetOperations<String, String> sops = mock(SetOperations.class);

        when(redis.opsForValue()).thenReturn(vops);
        when(redis.opsForSet()).thenReturn(sops);
        doAnswer(inv -> {
            values.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(vops).set(anyString(), anyString(), any(Duration.class));
        when(vops.get(anyString())).thenAnswer(inv -> values.get(inv.getArgument(0)));
        when(sops.add(anyString(), anyString())).thenAnswer(inv -> {
            sets.computeIfAbsent(inv.getArgument(0), k -> new HashSet<>()).add(inv.getArgument(1));
            return 1L;
        });
        when(sops.members(anyString())).thenAnswer(inv -> sets.get(inv.getArgument(0)));
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redis.delete(anyString())).thenAnswer(inv -> {
            values.remove(inv.getArgument(0));
            return true;
        });
        when(redis.delete(any(Collection.class))).thenAnswer(inv -> {
            Collection<?> keys = inv.getArgument(0);
            keys.forEach(k -> values.remove(k.toString()));
            return (long) keys.size();
        });
    }

    @Test
    void mintRoundTripsThroughResolve() {
        InstanceTokenService service = new InstanceTokenService(redis);
        ResolvedApiKey key = new ResolvedApiKey(
                UUID.randomUUID(), UUID.randomUUID(), "nxs_demo_partial12", List.of("registry:heartbeat"));

        InstanceTokenService.Issued issued = service.mint(key);

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresInSeconds()).isEqualTo(Duration.ofHours(1).toSeconds());
        Optional<ResolvedApiKey> resolved = service.resolve(issued.token());
        assertThat(resolved).isPresent();
        assertThat(resolved.get().projectId()).isEqualTo(key.projectId());
        assertThat(resolved.get().keyId()).isEqualTo(key.keyId());
        assertThat(resolved.get().keyPrefix()).isEqualTo("nxs_demo_partial12");
        assertThat(resolved.get().scopes()).containsExactly("registry:heartbeat");
    }

    /**
     * mint indexa el token bajo su key, así que revokeFor(keyId) lo borra de
     * Redis — el token deja de resolverse de inmediato, sin esperar al TTL
     * (comportamiento que ADR-0012 prometía; P2).
     */
    @Test
    void revokeForDeletesIndexedTokens() {
        InstanceTokenService service = new InstanceTokenService(redis);
        ResolvedApiKey key = new ResolvedApiKey(
                UUID.randomUUID(), UUID.randomUUID(), "nxs_demo_partial12", List.of("registry:heartbeat"));
        InstanceTokenService.Issued issued = service.mint(key);
        assertThat(service.resolve(issued.token())).isPresent();

        service.revokeFor(key.keyId());

        assertThat(service.resolve(issued.token())).isEmpty();
    }

    @Test
    void revokeForForUnknownKeyIsHarmless() {
        InstanceTokenService service = new InstanceTokenService(redis);
        service.revokeFor(UUID.randomUUID()); // no index → no-op, no explode
        assertThat(service.resolve("anything")).isEmpty();
    }

    @Test
    void resolveReturnsEmptyForUnknownToken() {
        InstanceTokenService service = new InstanceTokenService(redis);
        assertThat(service.resolve("unknown-token")).isEmpty();
        assertThat(service.resolve("")).isEmpty();
        assertThat(service.resolve(null)).isEmpty();
    }
}
