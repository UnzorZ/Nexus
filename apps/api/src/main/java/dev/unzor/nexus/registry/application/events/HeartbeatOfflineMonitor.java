package dev.unzor.nexus.registry.application.events;

import dev.unzor.nexus.registry.application.configuration.HeartbeatProperties;
import dev.unzor.nexus.registry.domain.entity.ProjectHeartbeat;
import dev.unzor.nexus.registry.domain.entity.ProjectRegistrySettings;
import dev.unzor.nexus.registry.persistence.repository.ProjectHeartbeatRepository;
import dev.unzor.nexus.registry.persistence.repository.ProjectRegistrySettingsRepository;
import dev.unzor.nexus.shared.audit.InstanceWentOffline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Barrido periódico que detecta instancias cuyo último latido supera el umbral de
 * "offline" (por defecto 5 min, dead-band anti-flapping) y dispara una alerta por
 * proyecto (si el proyecto la tiene activada con un destinatario).
 *
 * <p>Publica {@link InstanceWentOffline}; el envío real lo hace el módulo
 * {@code notify} (listener síncrono). La dedup es por outage y de ownership de
 * registry: {@code project_heartbeats.offline_notified_at} se setea al disparar y
 * se limpia al recuperar el latido (rearme).</p>
 *
 * <p>Cada operación de persistencia usa su propia tx (SimpleJpaRepository.save), así
 * un envío SMTP lento no mantiene abierta una tx larga. Despliegue multi-instancia:
 * la marca-antes-de-enviar no es atómica entre instancias (raza posible); hoy la
 * app es mono-instancia. Para multi-instancia, usar un UPDATE condicional.</p>
 */
@Component
public class HeartbeatOfflineMonitor {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatOfflineMonitor.class);

    private final ProjectHeartbeatRepository heartbeatRepository;
    private final ProjectRegistrySettingsRepository settingsRepository;
    private final HeartbeatProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public HeartbeatOfflineMonitor(
            ProjectHeartbeatRepository heartbeatRepository,
            ProjectRegistrySettingsRepository settingsRepository,
            HeartbeatProperties properties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.heartbeatRepository = heartbeatRepository;
        this.settingsRepository = settingsRepository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${nexus.registry.heartbeat.offline-scan-interval:60s}")
    public void sweep() {
        Instant now = Instant.now();
        Instant before = now.minusSeconds(properties.getOfflineNotifySeconds());
        for (ProjectHeartbeat beat : heartbeatRepository.findOfflineCandidates(before)) {
            try {
                notifyIfEnabled(beat, now);
            } catch (RuntimeException e) {
                // Un candidato problemático no debe abortar el barrido del resto.
                log.warn("Failed to process offline candidate instanceId={}: {}",
                        beat.getInstanceId(), e.getMessage());
            }
        }
    }

    private void notifyIfEnabled(ProjectHeartbeat beat, Instant now) {
        java.util.List<String> recipients = settingsRepository.findByProjectId(beat.getProjectId())
                .filter(ProjectRegistrySettings::isOfflineNotifyEnabled)
                .map(ProjectRegistrySettings::getOfflineNotifyRecipients)
                .orElse(java.util.List.of());
        if (recipients.isEmpty()) {
            return; // proyecto sin alerta activada o sin destinatarios
        }
        // Dedup antes de enviar: marca avisado aunque el SMTP falle después (no
        // re-spamea cada barrido; la recuperación al siguiente latido rearma).
        beat.markOfflineNotified(now);
        heartbeatRepository.save(beat);
        eventPublisher.publishEvent(new InstanceWentOffline(
                beat.getProjectId(), beat.getInstanceId(), beat.getAppName(),
                recipients, beat.getLastSeenAt()));
    }
}
