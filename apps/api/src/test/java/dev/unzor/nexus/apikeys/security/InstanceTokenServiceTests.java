package dev.unzor.nexus.apikeys.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

class InstanceTokenServiceTests {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);

    @SuppressWarnings("unchecked")
    private StringRedisTemplate redisMock() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(ops);
        return redis;
    }

    @Test
    void mintRoundTripsThroughResolve() {
        StringRedisTemplate redis = redisMock();
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        doNothing().when(ops).set(anyString(), value.capture(), any(Duration.class));
        when(ops.get(anyString())).thenAnswer(inv -> value.getValue());

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

    @Test
    void resolveReturnsEmptyForUnknownToken() {
        when(ops.get(anyString())).thenReturn(null);
        InstanceTokenService service = new InstanceTokenService(redisMock());

        assertThat(service.resolve("unknown-token")).isEmpty();
        assertThat(service.resolve("")).isEmpty();
        assertThat(service.resolve(null)).isEmpty();
    }
}
