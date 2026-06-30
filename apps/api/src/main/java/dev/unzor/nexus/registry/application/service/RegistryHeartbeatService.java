package dev.unzor.nexus.registry.application.service;

import dev.unzor.nexus.registry.api.dto.HeartbeatInstanceView;
import dev.unzor.nexus.registry.api.dto.HeartbeatReceipt;
import dev.unzor.nexus.registry.api.requests.HeartbeatRequest;
import dev.unzor.nexus.registry.application.configuration.HeartbeatProperties;
import dev.unzor.nexus.registry.domain.entity.ProjectHeartbeat;
import dev.unzor.nexus.registry.domain.enums.HeartbeatLiveness;
import dev.unzor.nexus.registry.persistence.repository.ProjectHeartbeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de heartbeat (spec §13.1). {@code record} hace upsert por la
 * identidad de la instancia {@code (projectId, instanceId)}; {@code listForProject}
 * deriva la liveness ONLINE/STALE/OFFLINE a partir de {@code lastSeenAt} y los
 * umbrales configurados. El {@code projectId} siempre llega del principal
 * resuelto (la API key), nunca del cuerpo.
 */
@Service
public class RegistryHeartbeatService {

    private final ProjectHeartbeatRepository repository;
    private final HeartbeatProperties properties;

    public RegistryHeartbeatService(ProjectHeartbeatRepository repository, HeartbeatProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public HeartbeatReceipt record(UUID projectId, UUID apiKeyId, HeartbeatRequest request, Instant now) {
        ProjectHeartbeat beat = repository.findByProjectIdAndInstanceId(projectId, request.instanceId())
                .map(existing -> {
                    existing.touch(apiKeyId, request.appName(), request.appVersion(), request.status(),
                            request.metadata(), now);
                    return existing;
                })
                .orElseGet(() -> new ProjectHeartbeat(
                        projectId, apiKeyId, request.instanceId(),
                        request.appName(), request.appVersion(), request.status(),
                        request.metadata(), now));
        repository.save(beat);
        return new HeartbeatReceipt(projectId, now, properties.getIntervalSeconds());
    }

    @Transactional(readOnly = true)
    public List<HeartbeatInstanceView> listForProject(UUID projectId) {
        Instant now = Instant.now();
        return repository.findAllByProjectId(projectId).stream()
                .map(beat -> HeartbeatInstanceView.from(beat, livenessOf(beat.getLastSeenAt(), now)))
                .toList();
    }

    /**
     * ONLINE dentro del intervalo de latido, STALE en la ventana de gracia,
     * OFFLINE tras el timeout.
     */
    HeartbeatLiveness livenessOf(Instant lastSeenAt, Instant now) {
        long elapsed = Duration.between(lastSeenAt, now).getSeconds();
        if (elapsed <= properties.getIntervalSeconds()) {
            return HeartbeatLiveness.ONLINE;
        }
        if (elapsed <= properties.getTimeoutSeconds()) {
            return HeartbeatLiveness.STALE;
        }
        return HeartbeatLiveness.OFFLINE;
    }
}
