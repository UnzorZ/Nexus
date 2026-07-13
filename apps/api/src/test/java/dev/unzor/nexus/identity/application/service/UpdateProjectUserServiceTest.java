package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link UpdateProjectUserService}: una edición explícita del username cambia
 * el subject OIDC y por ello revoca grants por el userId estable.
 */
class UpdateProjectUserServiceTest {

    private final ProjectUserRepository repository = mock(ProjectUserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProjectUserOAuthRevocationService oauthRevocation = mock(ProjectUserOAuthRevocationService.class);
    private final ProjectUserSessionService sessions = mock(ProjectUserSessionService.class);
    private final UpdateProjectUserService service =
            new UpdateProjectUserService(repository, eventPublisher, oauthRevocation, sessions);

    @Test
    void usernameChangeRevokesAuthorizationsByStableUserId() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProjectUser user = new ProjectUser(projectId, "neo@example.com", "hash", "Neo");
        when(repository.findByProjectIdAndId(projectId, userId)).thenReturn(Optional.of(user));
        when(repository.save(any(ProjectUser.class))).thenAnswer(i -> i.getArgument(0));

        service.update(projectId, userId, "Neo", "morpheus", UUID.randomUUID());

        assertThat(user.getAuthzVersion()).isEqualTo(1L);
        assertThat(user.getUsername()).isEqualTo("morpheus");
        verify(oauthRevocation).revokeForProjectUser(projectId, userId);
        verify(sessions).revokeAll(userId);
    }

    @Test
    void displayNameOnlyUpdateDoesNotRevokeAnything() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProjectUser user = new ProjectUser(projectId, "neo@example.com", "hash", "Neo");
        when(repository.findByProjectIdAndId(projectId, userId)).thenReturn(Optional.of(user));
        when(repository.save(any(ProjectUser.class))).thenAnswer(i -> i.getArgument(0));

        service.update(projectId, userId, "Thomas", null, UUID.randomUUID());

        assertThat(user.getAuthzVersion()).isZero();
        verify(oauthRevocation, never()).revokeForProjectUser(any(), any());
        verify(sessions, never()).revokeAll(any());
    }
}
