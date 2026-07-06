package dev.unzor.nexus.identity.application.events;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.ProjectUserAuthoritiesChanged;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectUserAuthoritiesChangeListenerTest {

    private final ProjectUserRepository repository = mock(ProjectUserRepository.class);
    private final ProjectUserAuthoritiesChangeListener listener = new ProjectUserAuthoritiesChangeListener(repository);

    @Test
    void incrementsAuthzVersionAndSaves() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProjectUser user = new ProjectUser(projectId, "alice@example.com", "hash", "Alice");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        assertThat(user.getAuthzVersion()).isZero();
        when(repository.findByProjectIdAndId(projectId, userId)).thenReturn(Optional.of(user));

        listener.onAuthoritiesChanged(new ProjectUserAuthoritiesChanged(projectId, userId));

        assertThat(user.getAuthzVersion()).isEqualTo(1L);
        verify(repository).save(user);
    }

    @Test
    void missingUserIsNoOp() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findByProjectIdAndId(projectId, userId)).thenReturn(Optional.empty());

        // A deleted user (orphan assignment) must not break the event flow.
        assertThatCode(() -> listener.onAuthoritiesChanged(new ProjectUserAuthoritiesChanged(projectId, userId)))
                .doesNotThrowAnyException();
    }
}
