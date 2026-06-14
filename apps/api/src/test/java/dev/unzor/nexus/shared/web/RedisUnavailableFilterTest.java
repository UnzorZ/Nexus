package dev.unzor.nexus.shared.web;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RedisUnavailableFilter#isRedisUnavailable(Throwable)}.
 *
 * <p>Verifies that only exception chains containing an unambiguous Redis/Lettuce signal
 * are treated as {@code redis_unavailable}; a generic {@link QueryTimeoutException}
 * without a Redis cause is not.
 */
class RedisUnavailableFilterTest {

    @Test
    void redisConnectionFailureIsUnavailable() {
        assertThat(RedisUnavailableFilter.isRedisUnavailable(
                new RedisConnectionFailureException("connection refused"))).isTrue();
    }

    @Test
    void lettuceCommandTimeoutWrappedInQueryTimeoutIsUnavailable() {
        RedisCommandTimeoutException lettuce = new RedisCommandTimeoutException("timed out");
        QueryTimeoutException wrapped = new QueryTimeoutException("Redis query timed out", lettuce);

        assertThat(RedisUnavailableFilter.isRedisUnavailable(wrapped)).isTrue();
    }

    @Test
    void lettuceConnectionExceptionIsUnavailable() {
        assertThat(RedisUnavailableFilter.isRedisUnavailable(
                new RedisConnectionException("unable to connect"))).isTrue();
    }

    @Test
    void genericQueryTimeoutWithoutRedisCauseIsNotUnavailable() {
        QueryTimeoutException generic = new QueryTimeoutException("JPA query timed out");

        assertThat(RedisUnavailableFilter.isRedisUnavailable(generic)).isFalse();
    }

    @Test
    void unrelatedRuntimeExceptionIsNotUnavailable() {
        assertThat(RedisUnavailableFilter.isRedisUnavailable(
                new IllegalStateException("something else"))).isFalse();
    }

    @Test
    void nullExceptionIsNotUnavailable() {
        assertThat(RedisUnavailableFilter.isRedisUnavailable(null)).isFalse();
    }
}
