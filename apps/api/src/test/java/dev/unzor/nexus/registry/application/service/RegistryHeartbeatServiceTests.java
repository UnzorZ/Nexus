package dev.unzor.nexus.registry.application.service;

import dev.unzor.nexus.instance.application.service.InstanceSettingsService;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.registry.api.dto.RegistrySettings;
import dev.unzor.nexus.registry.api.requests.HeartbeatRequest;
import dev.unzor.nexus.registry.application.configuration.HeartbeatProperties;
import dev.unzor.nexus.registry.domain.entity.ProjectHeartbeat;
import dev.unzor.nexus.registry.domain.entity.ProjectRegistrySettings;
import dev.unzor.nexus.registry.domain.enums.HeartbeatLiveness;
import dev.unzor.nexus.registry.domain.exception.InvalidRegistrySettingsException;
import dev.unzor.nexus.registry.persistence.repository.ProjectHeartbeatRepository;
import dev.unzor.nexus.registry.persistence.repository.ProjectRegistrySettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistryHeartbeatServiceTests {

    private final ProjectHeartbeatRepository repository = mock(ProjectHeartbeatRepository.class);
    private final ProjectRegistrySettingsRepository settingsRepository = mock(ProjectRegistrySettingsRepository.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final HeartbeatProperties properties = new HeartbeatProperties();
    private final InstanceSettingsService instanceSettings = stubInstanceSettings();
    private final RegistryHeartbeatService service = new RegistryHeartbeatService(
            repository, settingsRepository, projectLookupService, properties, instanceSettings,
            mock(ApplicationEventPublisher.class));

    private static InstanceSettingsService stubInstanceSettings() {
        InstanceSettingsService service = mock(InstanceSettingsService.class);
        when(service.heartbeatDefaults()).thenReturn(Optional.empty());
        return service;
    }

    @Test
    void recordCreatesNewInstanceWhenAbsent() {
        UUID projectId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-30T12:00:00Z");
        when(repository.findByProjectIdAndInstanceId(projectId, "demo-api-01")).thenReturn(Optional.empty());
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(repository.save(any(ProjectHeartbeat.class))).thenAnswer(i -> i.getArgument(0));

        var receipt = service.record(projectId, apiKeyId, "nxs_demo_partial12",
                new HeartbeatRequest("demo-api-01", "demo-api", "1.0.0", "up", Map.of("javaVersion", "21")), now);

        assertThat(receipt.projectId()).isEqualTo(projectId);
        assertThat(receipt.receivedAt()).isEqualTo(now);
        assertThat(receipt.nextHeartbeatInSeconds()).isEqualTo(30);
        verify(repository).save(any(ProjectHeartbeat.class));
    }

    @Test
    void recordUpdatesLastSeenAndVersionWhenInstanceExists() {
        UUID projectId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-30T12:00:00Z");
        ProjectHeartbeat existing = new ProjectHeartbeat(
                projectId, apiKeyId, "nxs_demo_oldprefix", "demo-api-01", "demo-api", "0.9.0", "up", null, now.minus(60, ChronoUnit.SECONDS));
        when(repository.findByProjectIdAndInstanceId(projectId, "demo-api-01")).thenReturn(Optional.of(existing));
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(repository.save(any(ProjectHeartbeat.class))).thenAnswer(i -> i.getArgument(0));

        service.record(projectId, apiKeyId, "nxs_demo_partial12",
                new HeartbeatRequest("demo-api-01", "demo-api", "1.0.0", "up", null), now);

        assertThat(existing.getLastSeenAt()).isEqualTo(now);
        assertThat(existing.getAppVersion()).isEqualTo("1.0.0");
        verify(repository).save(existing);
    }

    @Test
    void livenessDerivedFromLastSeenAndThresholds() {
        Instant now = Instant.parse("2026-06-30T12:00:00Z");
        // interval=30, timeout=90
        RegistryHeartbeatService.LivenessThresholds thresholds =
                new RegistryHeartbeatService.LivenessThresholds(30, 90);
        // within beat interval (<=30s) -> ONLINE
        assertThat(service.livenessOf(now.minus(10, ChronoUnit.SECONDS), now, thresholds)).isEqualTo(HeartbeatLiveness.ONLINE);
        // entered stale window (30s < .. ) -> STALE
        assertThat(service.livenessOf(now.minus(60, ChronoUnit.SECONDS), now, thresholds)).isEqualTo(HeartbeatLiveness.STALE);
        // stale extends THROUGH timeout boundary (interval < .. <= timeout) -> STALE
        assertThat(service.livenessOf(now.minus(75, ChronoUnit.SECONDS), now, thresholds)).isEqualTo(HeartbeatLiveness.STALE);
        assertThat(service.livenessOf(now.minus(90, ChronoUnit.SECONDS), now, thresholds)).isEqualTo(HeartbeatLiveness.STALE);
        // past timeout -> OFFLINE (timeout is the OFFLINE boundary)
        assertThat(service.livenessOf(now.minus(95, ChronoUnit.SECONDS), now, thresholds)).isEqualTo(HeartbeatLiveness.OFFLINE);
        assertThat(service.livenessOf(now.minus(120, ChronoUnit.SECONDS), now, thresholds)).isEqualTo(HeartbeatLiveness.OFFLINE);
    }

    @Test
    void saveSettingsPersistsOfflineNotifyConfig() {
        UUID projectId = UUID.randomUUID();
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(settingsRepository.save(any(ProjectRegistrySettings.class))).thenAnswer(i -> i.getArgument(0));

        RegistrySettings result = service.saveSettings(projectId, 30, 90, true, "ops@example.com", UUID.randomUUID());

        assertThat(result.offlineNotifyEnabled()).isTrue();
        assertThat(result.offlineNotifyEmail()).isEqualTo("ops@example.com");
        assertThat(result.intervalSeconds()).isEqualTo(30);
    }

    @Test
    void saveSettingsRequiresEmailWhenOfflineNotifyEnabled() {
        UUID projectId = UUID.randomUUID();
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveSettings(projectId, 30, 90, true, "  ", UUID.randomUUID()))
                .isInstanceOf(InvalidRegistrySettingsException.class);
    }

    @Test
    void saveSettingsRejectsInvalidEmailWhenEnabled() {
        UUID projectId = UUID.randomUUID();
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveSettings(projectId, 30, 90, true, "not-an-email", UUID.randomUUID()))
                .isInstanceOf(InvalidRegistrySettingsException.class);
    }

    @Test
    void saveSettingsClearsEmailWhenDisabled() {
        UUID projectId = UUID.randomUUID();
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(settingsRepository.save(any(ProjectRegistrySettings.class))).thenAnswer(i -> i.getArgument(0));

        RegistrySettings result = service.saveSettings(projectId, 30, 90, false, "ops@example.com", UUID.randomUUID());

        assertThat(result.offlineNotifyEnabled()).isFalse();
        assertThat(result.offlineNotifyEmail()).isNull();
    }

    @Test
    void saveSettingsPreservesOfflineNotifyWhenOmitted() {
        // El card del dashboard guarda sólo umbrales (offlineNotify null) → no debe
        // resetear la config de alerta offline existente.
        UUID projectId = UUID.randomUUID();
        ProjectRegistrySettings existing = new ProjectRegistrySettings(projectId, 30, 90);
        existing.updateOfflineNotify(true, "ops@example.com");
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(ProjectRegistrySettings.class))).thenAnswer(i -> i.getArgument(0));

        RegistrySettings result = service.saveSettings(projectId, 45, 120, null, null, UUID.randomUUID());

        assertThat(result.intervalSeconds()).isEqualTo(45);
        assertThat(result.timeoutSeconds()).isEqualTo(120);
        assertThat(result.offlineNotifyEnabled()).isTrue();
        assertThat(result.offlineNotifyEmail()).isEqualTo("ops@example.com");
    }
}
