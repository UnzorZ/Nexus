package dev.unzor.nexus.registry.domain.enums;

/**
 * Liveness derivada de un heartbeat (spec §13.1). Se calcula en lectura a
 * partir de {@code last_seen_at} y el timeout configurado; no se persiste.
 * ONLINE dentro del intervalo de latido, STALE en la ventana de gracia,
 * OFFLINE tras el timeout.
 */
public enum HeartbeatLiveness {
    ONLINE, STALE, OFFLINE
}
