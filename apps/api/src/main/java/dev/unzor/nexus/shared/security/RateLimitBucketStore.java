package dev.unzor.nexus.shared.security;

import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Almacén en memoria de los buckets token-bucket (bucket4j) por clave
 * {@code "<tier>:<clientIp>"}.
 *
 * <p>Los buckets son locales (no distribuidos): suficiente para una sola instancia
 * y para la defensa per-IP de los endpoints de auth pública. Para un despliegue
 * multi-instancia habría que moverlos a un backend distribuido de bucket4j
 * (Redis/Hazelcast), fuera del alcance actual (M6).
 *
 * <p>Evita el crecimiento no acotado del mapa expulsando periódicamente los buckets
 * {@code idle} (aquellos ya recargados a su capacidad máxima, i.e. sin consumo
 * reciente) vía {@link #evictIdleBuckets()}. Eliminar un bucket a tope no pierde
 * estado útil: si el cliente vuelve a pedir, se recrea lleno.
 */
@Component
public class RateLimitBucketStore {

    private static final Logger log = LoggerFactory.getLogger(RateLimitBucketStore.class);

    private final RateLimitProperties properties;
    private final ConcurrentMap<String, RateBucket> buckets = new ConcurrentHashMap<>();

    RateLimitBucketStore(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * Resuelve (creando si hace falta) el bucket para la clave dada.
     *
     * @param key  {@code "<tier>:<clientIp>"}
     * @param tier tier que determina capacidad y reposición
     */
    public Bucket bucketFor(String key, RateLimitTier tier) {
        return buckets.computeIfAbsent(key, k -> new RateBucket(buildBucket(tier), capacityFor(tier))).bucket();
    }

    /** Número de buckets activos (visor para tests y observabilidad). */
    public int size() {
        return buckets.size();
    }

    private Bucket buildBucket(RateLimitTier tier) {
        long capacity = capacityFor(tier);
        long refillTokens = refillTokensFor(tier);
        Duration refillPeriod = refillPeriodFor(tier);
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillIntervally(refillTokens, refillPeriod))
                .build();
    }

    private long capacityFor(RateLimitTier tier) {
        return tier == RateLimitTier.AUTH ? properties.authCapacity() : properties.generalCapacity();
    }

    private long refillTokensFor(RateLimitTier tier) {
        return tier == RateLimitTier.AUTH ? properties.authRefillTokens() : properties.generalRefillTokens();
    }

    private Duration refillPeriodFor(RateLimitTier tier) {
        return tier == RateLimitTier.AUTH ? properties.authRefillPeriod() : properties.generalRefillPeriod();
    }

    /**
     * Expulsa los buckets idle (recargados a capacidad máxima). Frecuencia:
     * {@code nexus.ratelimit.evict-interval}.
     */
    @Scheduled(fixedDelayString = "${nexus.ratelimit.evict-interval:5m}")
    void evictIdleBuckets() {
        if (buckets.isEmpty()) {
            return;
        }
        int before = buckets.size();
        buckets.entrySet().removeIf(e -> e.getValue().bucket().getAvailableTokens() >= e.getValue().capacity());
        int removed = before - buckets.size();
        if (removed > 0) {
            log.debug("Evicted {} idle rate-limit buckets ({} remaining)", removed, buckets.size());
        }
    }

    private record RateBucket(Bucket bucket, long capacity) {}
}
