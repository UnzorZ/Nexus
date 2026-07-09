package io.nexus.client;

import io.nexus.client.api.AuthorizationSnapshot;
import io.nexus.client.internal.PermissionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché de snapshots de autorización (spec §14.11 + §18). Para cada usuario
 * guarda el último snapshot fetched durante su TTL; {@link #can} resuelve el
 * permiso localmente con {@link PermissionMatcher} (comodines verbatim).
 *
 * <p>Política ante fallo de Nexus cuando el snapshot ha caducado:
 * <ul>
 *   <li>{@code fail-closed=true} (por defecto): deniega — más seguro.</li>
 *   <li>{@code fail-closed=false}: sirve el snapshot caducado (best-effort).</li>
 * </ul>
 * No reintenta ocultar fallos de seguridad (spec §18.3): un error de red es un
 * denegación cuando no hay snapshot válido.
 */
public class PermissionSnapshotCache {

    private static final Logger log = LoggerFactory.getLogger(PermissionSnapshotCache.class);

    private final PermissionClient permissionClient;
    private final Duration ttl;
    private final boolean failClosed;
    private final ConcurrentHashMap<UUID, Cached> cache = new ConcurrentHashMap<>();

    public PermissionSnapshotCache(PermissionClient permissionClient, Duration ttl, boolean failClosed) {
        this.permissionClient = permissionClient;
        this.ttl = ttl;
        this.failClosed = failClosed;
    }

    /**
     * ¿Tiene el usuario el permiso {@code key}? Deniega (false) si no hay
     * snapshot válido y Nexus no responde (fail-closed).
     */
    public boolean can(UUID userId, String key) {
        AuthorizationSnapshot snapshot = get(userId);
        if (snapshot == null) {
            return false;
        }
        return PermissionMatcher.matches(snapshot.permissions(), key);
    }

    /** Snapshot vigente para el usuario, o {@code null} si caducó y no se pudo refrescar. */
    public AuthorizationSnapshot get(UUID userId) {
        Instant now = Instant.now();
        Cached cached = cache.get(userId);
        if (cached != null && cached.validUntil().isAfter(now)) {
            return cached.snapshot();
        }
        try {
            AuthorizationSnapshot fresh = permissionClient.snapshot(userId);
            // La validez cacheada es el mínimo entre el expiresAt del backend y el
            // TTL configurado por el cliente (un snapshot-ttl menor acorta la ventana
            // de autorización cacheada, aunque el backend indique más tiempo).
            Instant validUntil = fresh.expiresAt().isBefore(now.plus(ttl)) ? fresh.expiresAt() : now.plus(ttl);
            cache.put(userId, new Cached(fresh, validUntil));
            return fresh;
        } catch (RuntimeException e) {
            log.warn("Permission snapshot fetch failed for user {}: {}", userId, e.getMessage());
            if (!failClosed && cached != null) {
                // Best-effort: sirve el caducado antes que denegar por una caída transitoria.
                return cached.snapshot();
            }
            return null;
        }
    }

    /** Invalida la entrada cacheada (p. ej. tras un back-channel logout del usuario). */
    public void invalidate(UUID userId) {
        cache.remove(userId);
    }

    public List<String> permissions(UUID userId) {
        AuthorizationSnapshot snapshot = get(userId);
        return snapshot == null ? List.of() : snapshot.permissions();
    }

    private record Cached(AuthorizationSnapshot snapshot, Instant validUntil) {}
}
