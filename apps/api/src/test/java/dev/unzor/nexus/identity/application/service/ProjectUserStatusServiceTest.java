package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link ProjectUserStatusService}: cubre la revocación de
 * tokens (bump de {@code authz_version} + revoca sesiones + audita).
 */
class ProjectUserStatusServiceTest {

    private final ProjectUserRepository repository = mock(ProjectUserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProjectUserSessionService sessions = mock(ProjectUserSessionService.class);
    private final ProjectUserStatusService service =
            new ProjectUserStatusService(repository, eventPublisher, sessions);

    @Test
    void revokeTokensBumpsAuthzVersionRevokesSessionsAndAudits() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        ProjectUser user = new ProjectUser(projectId, "neo@example.com", "hash", "Neo");
        when(repository.findByProjectIdAndId(projectId, userId)).thenReturn(Optional.of(user));
        when(repository.save(any(ProjectUser.class))).thenAnswer(i -> i.getArgument(0));

        service.revokeTokens(projectId, userId, actor);

        assertThat(user.getAuthzVersion()).isEqualTo(1L); // 0 → 1
        verify(sessions).revokeAll(userId);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo("project_user.tokens_revoked");
        assertThat(event.projectId()).isEqualTo(projectId);
    }
}
