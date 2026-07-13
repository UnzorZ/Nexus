package dev.unzor.nexus.identity.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectUserOAuthRevocationServiceTest {

    @Test
    void propagatesRevocationFailureToCallerTransaction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BackChannelLogoutClientResolver resolver = mock(BackChannelLogoutClientResolver.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        doThrow(new DataAccessResourceFailureException("PostgreSQL statement failed"))
                .when(jdbcTemplate).update(anyString(), any(Object[].class));
        ProjectUserOAuthRevocationService service =
                new ProjectUserOAuthRevocationService(jdbcTemplate, resolver, publisher);

        assertThatThrownBy(() -> service.revokeForProjectUser(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void snapshotsLogoutTargetsBeforeDeletingAuthorizationsAndPublishesThem() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BackChannelLogoutClientResolver resolver = mock(BackChannelLogoutClientResolver.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ProjectUserOAuthRevocationService service =
                new ProjectUserOAuthRevocationService(jdbcTemplate, resolver, publisher);
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BackChannelLogoutTarget target = new BackChannelLogoutTarget(
                UUID.randomUUID(), "client-a", "https://rp.example/logout");
        when(resolver.resolveForProjectUser(projectId, userId)).thenReturn(List.of(
                new BackChannelLogoutClientResolver.ResolvedLogout(
                        "alice", "https://nexus.example/p/acme", List.of(target))));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        service.revokeForProjectUser(projectId, userId);

        var order = inOrder(resolver, jdbcTemplate, publisher);
        order.verify(resolver).resolveForProjectUser(projectId, userId);
        order.verify(jdbcTemplate).update(anyString(), eq(projectId), eq(userId.toString()));
        var event = new BackChannelLogoutRequested(
                "alice", projectId, "https://nexus.example/p/acme", List.of(target));
        order.verify(publisher).publishEvent(event);
        assertThat(event.targets()).containsExactly(target);
    }
}
