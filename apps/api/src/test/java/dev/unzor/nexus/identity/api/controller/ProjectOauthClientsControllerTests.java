package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.api.dto.OauthClientCreated;
import dev.unzor.nexus.identity.api.dto.OauthClientSummary;
import dev.unzor.nexus.identity.api.requests.CreateOauthClientRequest;
import dev.unzor.nexus.identity.api.requests.UpdateOauthClientRequest;
import dev.unzor.nexus.identity.application.service.ProjectOauthClientsService;
import dev.unzor.nexus.identity.domain.enums.OauthClientStatus;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectOauthClientsControllerTests {

    private final ProjectOauthClientsService service = mock(ProjectOauthClientsService.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final ProjectOauthClientsController controller =
            new ProjectOauthClientsController(service, projectAccessService);

    @Test
    void listDelegatesAfterRequireAccess() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        when(service.listForProject(projectId)).thenReturn(List.of(summary()));

        controller.list(projectId, principal, authentication(principal, false));

        verify(projectAccessService).requireAccess(projectId, accountId, false);
        verify(service).listForProject(projectId);
    }

    @Test
    void createByManagerDelegatesWithAllFields() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        when(service.create(projectId, "Web", List.of("https://app/cb"), null,
                List.of("authorization_code"), List.of("openid"), true, true, false, accountId))
                .thenReturn(created());

        controller.create(projectId,
                new CreateOauthClientRequest("Web", List.of("https://app/cb"), null,
                        List.of("authorization_code"), List.of("openid"), true, true, false),
                principal, authentication(principal, false));

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(service).create(projectId, "Web", List.of("https://app/cb"), null,
                List.of("authorization_code"), List.of("openid"), true, true, false, accountId);
    }

    @Test
    void createByNonManagerThrowsPermissionDenied() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        doThrow(new ProjectAccessDeniedException(projectId, accountId))
                .when(projectAccessService).requireManage(projectId, accountId, false);

        assertThatThrownBy(() -> controller.create(projectId,
                new CreateOauthClientRequest("Web", List.of("https://app/cb"), null, null, null, true, true, false),
                principal, authentication(principal, false))).isInstanceOf(ProjectAccessDeniedException.class);

        verifyNoInteractions(service);
    }

    @Test
    void mutationsAllRequireManageAndDelegate() {
        UUID projectId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var auth = authentication(principal, false);

        controller.update(projectId, id,
                new UpdateOauthClientRequest("Web", List.of("https://app/cb"), null, List.of("openid"), OauthClientStatus.ACTIVE),
                principal, auth);
        controller.rotate(projectId, id, principal, auth);
        controller.disable(projectId, id, principal, auth);
        controller.delete(projectId, id, principal, auth);

        verify(projectAccessService, org.mockito.Mockito.times(4)).requireManage(projectId, accountId, false);
        verify(service).update(projectId, id, "Web", List.of("https://app/cb"), null, List.of("openid"), OauthClientStatus.ACTIVE, accountId);
        verify(service).rotateSecret(projectId, id, accountId);
        verify(service).disable(projectId, id, accountId);
        verify(service).delete(projectId, id, accountId);
    }

    private static UsernamePasswordAuthenticationToken authentication(AuthenticatedAccount principal, boolean instanceAdmin) {
        var authorities = instanceAdmin
                ? List.of(new SimpleGrantedAuthority("ROLE_INSTANCE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private static OauthClientSummary summary() {
        return new OauthClientSummary(UUID.randomUUID(), "nxo-x", "Web", List.of("https://app/cb"),
                List.of(), List.of("authorization_code"), List.of("openid"), true, false, true, "ACTIVE",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static OauthClientCreated created() {
        return new OauthClientCreated(UUID.randomUUID(), "nxo-x", "nxs-secret", "Web",
                List.of("https://app/cb"), List.of(), List.of("authorization_code"), List.of("openid"),
                true, false, true, "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }
}
