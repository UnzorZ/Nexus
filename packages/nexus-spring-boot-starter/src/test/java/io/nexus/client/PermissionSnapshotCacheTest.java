package io.nexus.client;

import io.nexus.client.api.AuthorizationSnapshot;
import io.nexus.client.internal.PermissionClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionSnapshotCacheTest {

    private final PermissionClient client = mock(PermissionClient.class);

    @Test
    void returnsCachedSnapshotWithinTtlWithoutRefetching() {
        PermissionSnapshotCache cache = new PermissionSnapshotCache(client, Duration.ofSeconds(30), true);
        UUID userId = UUID.randomUUID();
        when(client.snapshot(userId)).thenReturn(snapshot(userId, List.of("orders.*"), Instant.now().plusSeconds(30)));

        cache.get(userId);
        cache.get(userId);

        verify(client, times(1)).snapshot(userId);
        assertThat(cache.can(userId, "orders.read")).isTrue();
    }

    @Test
    void refetchesWhenSnapshotExpires() {
        PermissionSnapshotCache cache = new PermissionSnapshotCache(client, Duration.ofSeconds(30), true);
        UUID userId = UUID.randomUUID();
        // Primer fetch: snapshot YA caducado (expiresAt en el pasado) → cacheado pero expira al instante.
        when(client.snapshot(userId)).thenReturn(snapshot(userId, List.of("x"), Instant.now().minusSeconds(1)));
        cache.get(userId);
        // Segundo get: el cacheado está caducado → nuevo fetch (fresco).
        when(client.snapshot(userId)).thenReturn(snapshot(userId, List.of("orders.read"), Instant.now().plusSeconds(60)));
        cache.get(userId);
        verify(client, times(2)).snapshot(userId);
    }

    @Test
    void failClosedDeniesWhenFetchFailsAndNoValidSnapshot() {
        PermissionSnapshotCache cache = new PermissionSnapshotCache(client, Duration.ofSeconds(30), true);
        UUID userId = UUID.randomUUID();
        when(client.snapshot(userId)).thenThrow(new RuntimeException("nexus down"));

        assertThat(cache.get(userId)).isNull();
        assertThat(cache.can(userId, "orders.read")).isFalse();
    }

    @Test
    void failOpenServesStaleWhenFetchFails() {
        PermissionSnapshotCache cache = new PermissionSnapshotCache(client, Duration.ofSeconds(30), false);
        UUID userId = UUID.randomUUID();
        when(client.snapshot(userId)).thenReturn(snapshot(userId, List.of("orders.*"), Instant.now().minusSeconds(1)));
        cache.get(userId); // primer fetch (ya caducado en el camino, pero cacheado)
        when(client.snapshot(userId)).thenThrow(new RuntimeException("nexus down"));
        // Tras el fallo, fail-open sirve el caducado.
        assertThat(cache.can(userId, "orders.read")).isTrue();
    }

    @Test
    void resolvesWildcardsLocallyFromSnapshot() {
        PermissionSnapshotCache cache = new PermissionSnapshotCache(client, Duration.ofSeconds(30), true);
        UUID userId = UUID.randomUUID();
        when(client.snapshot(userId)).thenReturn(snapshot(userId, List.of("*"), Instant.now().plusSeconds(30)));
        assertThat(cache.can(userId, "anything")).isTrue();
        assertThat(cache.can(userId, "anything.else")).isTrue(); // cache hit, sin refetch
        verify(client, times(1)).snapshot(userId);
    }

    @Test
    void deniesWhenSnapshotIsDenyMarkerEvenWithPermissions() {
        // authzVersion < 0 = usuario inexistente/eliminado: denegación explícita aunque el
        // snapshot trajese permisos (remediación #3c; el backend ya los devuelve vacíos,
        // esto es defense-in-depth en el cliente).
        PermissionSnapshotCache cache = new PermissionSnapshotCache(client, Duration.ofSeconds(30), true);
        UUID userId = UUID.randomUUID();
        when(client.snapshot(userId)).thenReturn(
                new AuthorizationSnapshot(userId, UUID.randomUUID(), -1L, List.of("role"), List.of("orders.read"),
                        Instant.now().plusSeconds(30)));

        assertThat(cache.can(userId, "orders.read")).isFalse();
        assertThat(cache.permissions(userId)).isEmpty();
    }

    private static AuthorizationSnapshot snapshot(UUID userId, List<String> permissions, Instant expiresAt) {
        return new AuthorizationSnapshot(userId, UUID.randomUUID(), 1L, List.of("role"), permissions, expiresAt);
    }
}
