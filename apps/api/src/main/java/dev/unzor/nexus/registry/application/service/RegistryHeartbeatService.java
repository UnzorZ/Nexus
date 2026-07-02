package dev.unzor.nexus.registry.application.service;

import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.registry.api.dto.HeartbeatInstanceView;
import dev.unzor.nexus.registry.api.dto.HeartbeatReceipt;
import dev.unzor.nexus.registry.api.dto.RegistrySettings;
import dev.unzor.nexus.registry.api.requests.HeartbeatRequest;
import dev.unzor.nexus.registry.application.configuration.HeartbeatProperties;
import dev.unzor.nexus.registry.domain.entity.ProjectHeartbeat;
import dev.unzor.nexus.registry.domain.entity.ProjectRegistrySettings;
import dev.unzor.nexus.registry.domain.enums.HeartbeatLiveness;
import dev.unzor.nexus.registry.domain.exception.InvalidRegistrySettingsException;
import dev.unzor.nexus.registry.persistence.repository.ProjectHeartbeatRepository;
import dev.unzor.nexus.registry.persistence.repository.ProjectRegistrySettingsRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casos de uso de heartbeat (spec §13.1). {@code record} hace upsert por la
 * identidad de la instancia {@code (projectId, instanceId)}; {@code listForProject}
 * deriva la liveness ONLINE/STALE/OFFLINE a partir de {@code lastSeenAt} y los
 * umbrales del proyecto (o los defaults globales). El {@code projectId} siempre
 * llega del principal resuelto (la API key), nunca del cuerpo.
 */
@Service
public class RegistryHeartbeatService {

    private final ProjectHeartbeatRepository repository;
    private final ProjectRegistrySettingsRepository settingsRepository;
    private final ProjectLookupService projectLookupService;
    private final HeartbeatProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public RegistryHeartbeatService(ProjectHeartbeatRepository repository,
                                    ProjectRegistrySettingsRepository settingsRepository,
                                    ProjectLookupService projectLookupService,
                                    HeartbeatProperties properties,
                                    ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.settingsRepository = settingsRepository;
        this.projectLookupService = projectLookupService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public HeartbeatReceipt record(UUID projectId, UUID apiKeyId, String apiKeyPrefix,
                                   HeartbeatRequest request, Instant now) {
        ProjectHeartbeat beat = repository.findByProjectIdAndInstanceId(projectId, request.instanceId())
                .map(existing -> {
                    existing.touch(apiKeyId, apiKeyPrefix, request.appName(), request.appVersion(), request.status(),
                            request.metadata(), now);
                    return existing;
                })
                .orElseGet(() -> new ProjectHeartbeat(
                        projectId, apiKeyId, apiKeyPrefix, request.instanceId(),
                        request.appName(), request.appVersion(), request.status(),
                        request.metadata(), now));
        repository.save(beat);
        return new HeartbeatReceipt(projectId, now, resolveThresholds(projectId).intervalSeconds());
    }

    @Transactional(readOnly = true)
    public List<HeartbeatInstanceView> listForProject(UUID projectId) {
        Instant now = Instant.now();
        LivenessThresholds thresholds = resolveThresholds(projectId);
        return repository.findAllByProjectId(projectId).stream()
                .map(beat -> HeartbeatInstanceView.from(beat, livenessOf(beat.getLastSeenAt(), now, thresholds)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RegistrySettings getSettings(UUID projectId) {
        projectLookupService.requireById(projectId);
        return settingsRepository.findByProjectId(projectId)
                .map(RegistrySettings::from)
                .orElseGet(() -> RegistrySettings.defaults(projectId,
                        properties.getIntervalSeconds(), properties.getTimeoutSeconds()));
    }

    @Transactional
    public RegistrySettings saveSettings(UUID projectId, int intervalSeconds, int timeoutSeconds,
                                         UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        if (intervalSeconds < 1 || timeoutSeconds < intervalSeconds) {
            throw new InvalidRegistrySettingsException(
                    "Require 1 <= interval <= timeout.");
        }
        ProjectRegistrySettings settings = settingsRepository.findByProjectId(projectId)
                .orElse(new ProjectRegistrySettings(projectId, intervalSeconds, timeoutSeconds));
        settings.update(intervalSeconds, timeoutSeconds);
        ProjectRegistrySettings saved = settingsRepository.save(settings);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "registry.settings.updated", "registry_settings",
                projectId.toString(), actorAccountId,
                Map.of("interval", intervalSeconds, "timeout", timeoutSeconds)));
        return RegistrySettings.from(saved);
    }

    /** Umbrales efectivos: override del proyecto o defaults globales. */
    private LivenessThresholds resolveThresholds(UUID projectId) {
        return settingsRepository.findByProjectId(projectId)
                .map(s -> new LivenessThresholds(s.getIntervalSeconds(), s.getTimeoutSeconds()))
                .orElseGet(() -> new LivenessThresholds(
                        properties.getIntervalSeconds(), properties.getTimeoutSeconds()));
    }

    /**
     * ONLINE dentro del intervalo, STALE hasta el timeout, OFFLINE a partir del
     * timeout (spec §13.1: offline tras {@code timeoutSeconds}). El timeout decide
     * cuándo una instancia se da por muerta.
     */
    HeartbeatLiveness livenessOf(Instant lastSeenAt, Instant now, LivenessThresholds thresholds) {
        long elapsed = Duration.between(lastSeenAt, now).getSeconds();
        if (elapsed <= thresholds.intervalSeconds()) {
            return HeartbeatLiveness.ONLINE;
        }
        if (elapsed <= thresholds.timeoutSeconds()) {
            return HeartbeatLiveness.STALE;
        }
        return HeartbeatLiveness.OFFLINE;
    }

    record LivenessThresholds(int intervalSeconds, int timeoutSeconds) {
    }
}
