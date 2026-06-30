package dev.unzor.nexus.registry.application.service;

import dev.unzor.nexus.registry.api.requests.HeartbeatRequest;
import dev.unzor.nexus.registry.application.configuration.HeartbeatProperties;
import dev.unzor.nexus.registry.domain.entity.ProjectHeartbeat;
import dev.unzor.nexus.registry.domain.enums.HeartbeatLiveness;
import dev.unzor.nexus.registry.persistence.repository.ProjectHeartbeatRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistryHeartbeatServiceTests {

    private final ProjectHeartbeatRepository repository = mock(ProjectHeartbeatRepository.class);
    private final HeartbeatProperties properties = new HeartbeatProperties();
    private final RegistryHeartbeatService service = new RegistryHeartbeatService(repository, properties);

    @Test
    void recordCreatesNewInstanceWhenAbsent() {
        UUID projectId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-30T12:00:00Z");
        when(repository.findByProjectIdAndInstanceId(projectId, "demo-api-01")).thenReturn(Optional.empty());
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
        // within beat interval (<=30s) -> ONLINE
        assertThat(service.livenessOf(now.minus(10, ChronoUnit.SECONDS), now)).isEqualTo(HeartbeatLiveness.ONLINE);
        // grace window (30s < .. <= 90s) -> STALE
        assertThat(service.livenessOf(now.minus(60, ChronoUnit.SECONDS), now)).isEqualTo(HeartbeatLiveness.STALE);
        // past timeout (>90s) -> OFFLINE
        assertThat(service.livenessOf(now.minus(120, ChronoUnit.SECONDS), now)).isEqualTo(HeartbeatLiveness.OFFLINE);
    }
}
