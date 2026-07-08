package dev.unzor.nexus.shared.security;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitBucketStoreTest {

    private static RateLimitProperties props() {
        return new RateLimitProperties(
                true, false, Duration.ofMinutes(5),
                2, 2, Duration.ofSeconds(1),
                2, 2, Duration.ofSeconds(1));
    }

    @Test
    void evictsIdleBucketsButKeepsActiveOnes() {
        RateLimitBucketStore store = new RateLimitBucketStore(props());

        // Bucket idle: creado y nunca consumido → a capacidad máxima.
        Bucket idle = store.bucketFor("AUTH:1.1.1.1", RateLimitTier.AUTH);
        assertThat(idle.getAvailableTokens()).isEqualTo(2);

        // Bucket activo: consumido una vez → por debajo de la capacidad.
        Bucket active = store.bucketFor("AUTH:2.2.2.2", RateLimitTier.AUTH);
        assertThat(active.tryConsume(1)).isTrue();
        assertThat(active.getAvailableTokens()).isLessThan(2);

        assertThat(store.size()).isEqualTo(2);

        store.evictIdleBuckets();

        assertThat(store.size()).as("idle bucket should be evicted").isEqualTo(1);
        // El bucket activo sobrevive y conserva su instancia/estado.
        assertThat(store.bucketFor("AUTH:2.2.2.2", RateLimitTier.AUTH)).isSameAs(active);
    }
}
