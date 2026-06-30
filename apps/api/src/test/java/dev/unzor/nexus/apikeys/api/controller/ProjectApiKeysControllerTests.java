package dev.unzor.nexus.apikeys.api.controller;

import dev.unzor.nexus.apikeys.api.dto.ApiKeyCreated;
import dev.unzor.nexus.apikeys.api.dto.ApiKeySummary;
import dev.unzor.nexus.apikeys.api.requests.CreateApiKeyRequest;
import dev.unzor.nexus.apikeys.api.requests.UpdateApiKeyRequest;
import dev.unzor.nexus.apikeys.application.service.ProjectApiKeysService;
import dev.unzor.nexus.apikeys.domain.enums.ApiKeyStatus;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectApiKeysControllerTests {

    private final ProjectApiKeysService service = mock(ProjectApiKeysService.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final ProjectApiKeysController controller =
            new ProjectApiKeysController(service, projectAccessService);

    @Test
    void listDelegatesAfterRequireAccess() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        when(service.listForProject(projectId)).thenReturn(List.of());

        controller.list(projectId, principal, authentication(principal));

        verify(projectAccessService).requireAccess(projectId, accountId, false);
        verify(service).listForProject(projectId);
    }

    @Test
    void createByMemberThrowsPermissionDenied() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        doThrow(new ProjectAccessDeniedException(projectId, accountId))
                .when(projectAccessService).requireManage(projectId, accountId, false);

        assertThatThrownBy(() -> controller.create(
                projectId,
                new CreateApiKeyRequest("ci", List.of("registry:heartbeat"), null),
                principal,
                authentication(principal)
        )).isInstanceOf(ProjectAccessDeniedException.class);

        verifyNoInteractions(service);
    }

    @Test
    void createByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        when(service.create(projectId, "ci", List.of("registry:heartbeat"), null, accountId))
                .thenReturn(new ApiKeyCreated(
                        UUID.randomUUID(), "ci", "prefix", "nxs_shop_secret", ApiKeyStatus.ACTIVE,
                        List.of("registry:heartbeat"), null, null, null));

        controller.create(
                projectId,
                new CreateApiKeyRequest("ci", List.of("registry:heartbeat"), null),
                principal,
                authentication(principal)
        );

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(service).create(projectId, "ci", List.of("registry:heartbeat"), null, accountId);
    }

    @Test
    void rotateByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;

        controller.rotate(projectId, keyId, principal, authentication(principal));

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(service).rotate(projectId, keyId, accountId);
    }

    @Test
    void deleteByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;

        controller.delete(projectId, keyId, principal, authentication(principal));

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(service).delete(projectId, keyId, accountId);
    }

    private static UsernamePasswordAuthenticationToken authentication(AuthenticatedAccount principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
