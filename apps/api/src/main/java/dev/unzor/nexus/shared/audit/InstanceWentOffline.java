package dev.unzor.nexus.shared.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Señala que una instancia registrada ha superado el umbral de "offline"
 * (sin latido durante más de {@code nexus.registry.heartbeat.offline-notify-seconds},
 * por defecto 5 min) y debe notificarse a los destinatarios configurados del
 * proyecto.
 *
 * <p>Lo publica el módulo {@code registry} (barrido programado
 * {@code HeartbeatOfflineMonitor}) y lo consume el módulo {@code notify}, que
 * hace fan-out del email a cada destinatario. Vive en {@code shared.audit} (ya
 * publicado como {@code @NamedInterface("AuditEvents")} y consumido por ambos
 * módulos) para evitar una dependencia cíclica en compilación y no declarar un
 * nuevo {@code @NamedInterface} (lo que provocaría el CGLIB-proxy trap de
 * Modulith sobre filtros {@code GenericFilterBean}). Mismo patrón que
 * {@link ProjectUserAuthoritiesChanged}.</p>
 */
public record InstanceWentOffline(
        UUID projectId,
        String instanceId,
        String appName,
        List<String> recipients,
        Instant lastSeenAt
) {
}
