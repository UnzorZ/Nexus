package dev.unzor.nexus.notify.application.events;

import dev.unzor.nexus.notify.application.service.ProjectNotificationsService;
import dev.unzor.nexus.shared.audit.InstanceWentOffline;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InstanceOfflineNotifierTest {

    private final ProjectNotificationsService notificationsService = mock(ProjectNotificationsService.class);
    private final InstanceOfflineNotifier notifier = new InstanceOfflineNotifier(notificationsService);

    @Test
    void fansOutOneEmailPerRecipientWithInstanceDetails() {
        UUID projectId = UUID.randomUUID();
        Instant lastSeen = Instant.parse("2026-07-06T10:00:00Z");

        // Fan-out: un envío por destinatario.
        notifier.onInstanceWentOffline(new InstanceWentOffline(
                projectId, "inst-1", "My App",
                List.of("ops@example.com", "ops2@example.com"), lastSeen));

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationsService, times(2)).send(
                eq(projectId), recipient.capture(), isNull(),
                subject.capture(), body.capture(), isNull(), isNull());

        assertThat(recipient.getAllValues())
                .containsExactlyInAnyOrder("ops@example.com", "ops2@example.com");
        assertThat(subject.getValue()).contains("My App");
        assertThat(body.getValue()).contains("inst-1");
        assertThat(body.getValue()).contains("2026-07-06T10:00:00Z");
    }
}
