package dev.unzor.nexus.permissions.api.controller;

import dev.unzor.nexus.permissions.api.dto.PermissionDetails;
import dev.unzor.nexus.permissions.api.requests.CreatePermissionRequest;
import dev.unzor.nexus.permissions.api.requests.UpdatePermissionRequest;
import dev.unzor.nexus.permissions.application.service.ProjectPermissionsService;
import dev.unzor.nexus.permissions.domain.enums.PermissionSource;
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

class ProjectPermissionsControllerTests {

    private final ProjectPermissionsService permissionsService = mock(ProjectPermissionsService.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final ProjectPermissionsController controller =
            new ProjectPermissionsController(permissionsService, projectAccessService);

    @Test
    void listDelegatesAfterRequireAccess() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        when(permissionsService.listForProject(projectId)).thenReturn(List.of());

        controller.list(projectId, principal, authentication(principal));

        verify(projectAccessService).requireAccess(projectId, accountId, false);
        verify(permissionsService).listForProject(projectId);
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
                new CreatePermissionRequest("orders.cancel", "Cancel", null),
                principal,
                authentication(principal)
        )).isInstanceOf(ProjectAccessDeniedException.class);

        verifyNoInteractions(permissionsService);
    }

    @Test
    void createByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        when(permissionsService.create(projectId, "orders.cancel", "Cancel", null, accountId))
                .thenReturn(new PermissionDetails(UUID.randomUUID(), "orders.cancel", "Cancel", null, PermissionSource.WEB));

        controller.create(
                projectId,
                new CreatePermissionRequest("orders.cancel", "Cancel", null),
                principal,
                authentication(principal)
        );

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(permissionsService).create(projectId, "orders.cancel", "Cancel", null, accountId);
    }

    @Test
    void updateByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;

        controller.update(
                projectId,
                permissionId,
                new UpdatePermissionRequest("Cancel", "desc"),
                principal,
                authentication(principal)
        );

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(permissionsService).update(projectId, permissionId, "Cancel", "desc", accountId);
    }

    @Test
    void deleteByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;

        controller.delete(projectId, permissionId, principal, authentication(principal));

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(permissionsService).delete(projectId, permissionId, accountId);
    }

    private static UsernamePasswordAuthenticationToken authentication(AuthenticatedAccount principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
