package dev.unzor.nexus.permissions.api.controller;

import dev.unzor.nexus.permissions.api.dto.RoleDetails;
import dev.unzor.nexus.permissions.api.requests.CreateRoleRequest;
import dev.unzor.nexus.permissions.api.requests.SetRolePermissionsRequest;
import dev.unzor.nexus.permissions.application.service.ProjectRolesService;
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

class ProjectRolesControllerTests {

    private final ProjectRolesService rolesService = mock(ProjectRolesService.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final ProjectRolesController controller =
            new ProjectRolesController(rolesService, projectAccessService);

    @Test
    void listDelegatesAfterRequireAccess() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        when(rolesService.listForProject(projectId)).thenReturn(List.of());

        controller.list(projectId, principal, authentication(principal));

        verify(projectAccessService).requireAccess(projectId, accountId, false);
        verify(rolesService).listForProject(projectId);
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
                new CreateRoleRequest("admin", "Admin", null),
                principal,
                authentication(principal)
        )).isInstanceOf(ProjectAccessDeniedException.class);

        verifyNoInteractions(rolesService);
    }

    @Test
    void setPermissionsByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        when(rolesService.setPermissions(projectId, roleId, List.of("orders.*"), accountId))
                .thenReturn(new RoleDetails(roleId, "admin", "Admin", null, false, List.of("orders.*")));

        controller.setPermissions(
                projectId,
                roleId,
                new SetRolePermissionsRequest(List.of("orders.*")),
                principal,
                authentication(principal)
        );

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(rolesService).setPermissions(projectId, roleId, List.of("orders.*"), accountId);
    }

    @Test
    void setPermissionsByMemberThrowsPermissionDenied() {
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        doThrow(new ProjectAccessDeniedException(projectId, accountId))
                .when(projectAccessService).requireManage(projectId, accountId, false);

        assertThatThrownBy(() -> controller.setPermissions(
                projectId,
                roleId,
                new SetRolePermissionsRequest(List.of("orders.*")),
                principal,
                authentication(principal)
        )).isInstanceOf(ProjectAccessDeniedException.class);

        verifyNoInteractions(rolesService);
    }

    private static UsernamePasswordAuthenticationToken authentication(AuthenticatedAccount principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
