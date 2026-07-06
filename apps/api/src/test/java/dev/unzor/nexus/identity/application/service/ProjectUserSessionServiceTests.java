package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectUserSessionServiceTests {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RedisIndexedSessionRepository> provider = mock(ObjectProvider.class);
    private final RedisIndexedSessionRepository sessionRepository = mock(RedisIndexedSessionRepository.class);

    @Test
    void revokeAllDeletesEveryIndexedSessionForTheUser() {
        UUID userId = UUID.randomUUID();
        when(provider.getIfAvailable()).thenReturn(sessionRepository);
        when(sessionRepository.findByPrincipalName(NexusSessionAttributes.projectUserIndexValue(userId)))
                .thenReturn(Map.of("s1", mock(RedisSession.class), "s2", mock(RedisSession.class)));

        new ProjectUserSessionService(provider).revokeAll(userId);

        verify(sessionRepository).deleteById("s1");
        verify(sessionRepository).deleteById("s2");
    }

    @Test
    void revokeAllIsBestEffortWhenRepositoryUnavailable() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThatCode(() -> new ProjectUserSessionService(provider).revokeAll(UUID.randomUUID()))
                .doesNotThrowAnyException();
        verify(sessionRepository, never()).deleteById(any());
    }

    @Test
    void revokeAllSwallowsRedisErrors() {
        when(provider.getIfAvailable()).thenReturn(sessionRepository);
        when(sessionRepository.findByPrincipalName(any())).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> new ProjectUserSessionService(provider).revokeAll(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }
}
