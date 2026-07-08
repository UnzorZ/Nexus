package dev.unzor.nexus.registry.application.events;

import dev.unzor.nexus.registry.application.configuration.HeartbeatProperties;
import dev.unzor.nexus.registry.domain.entity.ProjectHeartbeat;
import dev.unzor.nexus.registry.domain.entity.ProjectRegistrySettings;
import dev.unzor.nexus.registry.persistence.repository.ProjectHeartbeatRepository;
import dev.unzor.nexus.registry.persistence.repository.ProjectRegistrySettingsRepository;
import dev.unzor.nexus.shared.audit.InstanceWentOffline;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeartbeatOfflineMonitorTest {

    private final ProjectHeartbeatRepository heartbeatRepository = mock(ProjectHeartbeatRepository.class);
    private final ProjectRegistrySettingsRepository settingsRepository = mock(ProjectRegistrySettingsRepository.class);
    private final HeartbeatProperties properties = new HeartbeatProperties();
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final HeartbeatOfflineMonitor monitor =
            new HeartbeatOfflineMonitor(heartbeatRepository, settingsRepository, properties, eventPublisher);

    @Test
    void publishesAlertAndMarksNotifiedWhenProjectEnabledWithRecipient() {
        UUID projectId = UUID.randomUUID();
        ProjectHeartbeat beat = beat(projectId, "inst-1", "My App");
        when(heartbeatRepository.findOfflineCandidates(any())).thenReturn(List.of(beat));
        ProjectRegistrySettings settings = new ProjectRegistrySettings(projectId, 30, 90);
        settings.updateOfflineNotify(true, List.of("ops@example.com"));
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.of(settings));
        when(heartbeatRepository.save(any(ProjectHeartbeat.class))).thenAnswer(i -> i.getArgument(0));

        monitor.sweep();

        assertThat(beat.getOfflineNotifiedAt()).isNotNull(); // dedup marcado
        verify(heartbeatRepository).save(beat);
        ArgumentCaptor<InstanceWentOffline> captor = ArgumentCaptor.forClass(InstanceWentOffline.class);
        verify(eventPublisher).publishEvent(captor.capture());
        InstanceWentOffline event = captor.getValue();
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.instanceId()).isEqualTo("inst-1");
        assertThat(event.appName()).isEqualTo("My App");
        assertThat(event.recipients()).containsExactly("ops@example.com");
    }

    @Test
    void doesNotPublishWhenProjectDisabled() {
        UUID projectId = UUID.randomUUID();
        ProjectHeartbeat beat = beat(projectId, "inst-2", "App");
        when(heartbeatRepository.findOfflineCandidates(any())).thenReturn(List.of(beat));
        ProjectRegistrySettings settings = new ProjectRegistrySettings(projectId, 30, 90); // enabled=false
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.of(settings));

        monitor.sweep();

        verify(eventPublisher, never()).publishEvent(any());
        verify(heartbeatRepository, never()).save(any(ProjectHeartbeat.class));
        assertThat(beat.getOfflineNotifiedAt()).isNull();
    }

    @Test
    void doesNotPublishWhenEnabledButEmailMissing() {
        UUID projectId = UUID.randomUUID();
        ProjectHeartbeat beat = beat(projectId, "inst-3", "App");
        when(heartbeatRepository.findOfflineCandidates(any())).thenReturn(List.of(beat));
        ProjectRegistrySettings settings = new ProjectRegistrySettings(projectId, 30, 90);
        settings.updateOfflineNotify(true, null); // enabled pero sin email
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.of(settings));

        monitor.sweep();

        verify(eventPublisher, never()).publishEvent(any());
        verify(heartbeatRepository, never()).save(any(ProjectHeartbeat.class));
    }

    @Test
    void doesNotPublishWhenNoProjectSettings() {
        UUID projectId = UUID.randomUUID();
        ProjectHeartbeat beat = beat(projectId, "inst-4", "App");
        when(heartbeatRepository.findOfflineCandidates(any())).thenReturn(List.of(beat));
        when(settingsRepository.findByProjectId(projectId)).thenReturn(Optional.empty());

        monitor.sweep();

        verify(eventPublisher, never()).publishEvent(any());
    }

    private static ProjectHeartbeat beat(UUID projectId, String instanceId, String appName) {
        return new ProjectHeartbeat(projectId, UUID.randomUUID(), "nxs_x_partial", instanceId,
                appName, "1.0.0", "up", null, Instant.now().minusSeconds(600));
    }
}
