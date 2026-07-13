package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectUserSessionServiceTests {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RedisIndexedSessionRepository> provider = mock(ObjectProvider.class);
    private final RedisIndexedSessionRepository sessionRepository = mock(RedisIndexedSessionRepository.class);
    private final ProjectUserRepository projectUserRepository = mock(ProjectUserRepository.class);

    @Test
    void revokeAllDeletesEveryIndexedSessionForTheUser() {
        UUID userId = UUID.randomUUID();
        when(provider.getIfAvailable()).thenReturn(sessionRepository);
        when(sessionRepository.findByPrincipalName(NexusSessionAttributes.projectUserIndexValue(userId)))
                .thenReturn(Map.of("s1", mock(RedisSession.class), "s2", mock(RedisSession.class)));

        service().revokeAll(userId);

        verify(sessionRepository).deleteById("s1");
        verify(sessionRepository).deleteById("s2");
    }

    @Test
    void revokeAllIsBestEffortWhenRepositoryUnavailable() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThatCode(() -> service().revokeAll(UUID.randomUUID()))
                .doesNotThrowAnyException();
        verify(sessionRepository, never()).deleteById(any());
    }

    @Test
    void revokeAllSwallowsRedisErrors() {
        when(provider.getIfAvailable()).thenReturn(sessionRepository);
        when(sessionRepository.findByPrincipalName(any())).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> service().revokeAll(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    void revokeAllForProjectDeletesAuthenticatedAndMfaPendingSessionsForEveryUser() {
        UUID projectId = UUID.randomUUID();
        UUID userOne = UUID.randomUUID();
        UUID userTwo = UUID.randomUUID();
        when(projectUserRepository.findIdsByProjectId(projectId)).thenReturn(java.util.List.of(userOne, userTwo));
        when(provider.getIfAvailable()).thenReturn(sessionRepository);
        when(sessionRepository.findByPrincipalName(NexusSessionAttributes.projectUserIndexValue(userOne)))
                .thenReturn(Map.of("authenticated", mock(RedisSession.class), "mfa-pending", mock(RedisSession.class)));
        when(sessionRepository.findByPrincipalName(NexusSessionAttributes.projectUserIndexValue(userTwo)))
                .thenReturn(Map.of("other-user", mock(RedisSession.class)));

        service().revokeAllForProject(projectId);

        verify(sessionRepository).deleteById("authenticated");
        verify(sessionRepository).deleteById("mfa-pending");
        verify(sessionRepository).deleteById("other-user");
    }

    @Test
    void revokeAllForProjectPropagatesRedisFailureSoArchiveCannotCommit() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RuntimeException redisFailure = new RuntimeException("redis down");
        when(projectUserRepository.findIdsByProjectId(projectId)).thenReturn(java.util.List.of(userId));
        when(provider.getIfAvailable()).thenReturn(sessionRepository);
        when(sessionRepository.findByPrincipalName(any())).thenThrow(redisFailure);

        assertThatThrownBy(() -> service().revokeAllForProject(projectId))
                .isSameAs(redisFailure);
    }

    @Test
    void revokeAllForEmptyProjectDoesNotRequireRedis() {
        UUID projectId = UUID.randomUUID();
        when(projectUserRepository.findIdsByProjectId(projectId)).thenReturn(java.util.List.of());

        assertThatCode(() -> service().revokeAllForProject(projectId)).doesNotThrowAnyException();

        verify(provider, never()).getIfAvailable();
    }

    private ProjectUserSessionService service() {
        return new ProjectUserSessionService(provider, projectUserRepository);
    }
}
