package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.api.dto.ProjectUserDetails;
import dev.unzor.nexus.identity.api.requests.CreateProjectUserRequest;
import dev.unzor.nexus.identity.api.requests.ResetPasswordRequest;
import dev.unzor.nexus.identity.api.requests.UpdateProjectUserRequest;
import dev.unzor.nexus.identity.application.service.CreateProjectUserService;
import dev.unzor.nexus.identity.application.service.DeleteProjectUserService;
import dev.unzor.nexus.identity.application.service.ProjectUserQueryService;
import dev.unzor.nexus.identity.application.service.ProjectUserStatusService;
import dev.unzor.nexus.identity.application.service.ResetProjectUserPasswordService;
import dev.unzor.nexus.identity.application.service.UpdateProjectUserService;
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

class ProjectUsersControllerTests {

    private final ProjectUserQueryService queryService = mock(ProjectUserQueryService.class);
    private final CreateProjectUserService createService = mock(CreateProjectUserService.class);
    private final UpdateProjectUserService updateService = mock(UpdateProjectUserService.class);
    private final ProjectUserStatusService statusService = mock(ProjectUserStatusService.class);
    private final DeleteProjectUserService deleteService = mock(DeleteProjectUserService.class);
    private final ResetProjectUserPasswordService resetPasswordService = mock(ResetProjectUserPasswordService.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final ProjectUsersController controller = new ProjectUsersController(
            queryService, createService, updateService, statusService,
            deleteService, resetPasswordService, projectAccessService);

    @Test
    void listDelegatesAfterRequireAccess() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        when(queryService.list(projectId)).thenReturn(List.of(details()));

        controller.list(projectId, principal, authentication(principal, false));

        verify(projectAccessService).requireAccess(projectId, accountId, false);
        verify(queryService).list(projectId);
    }

    @Test
    void createByManagerDelegatesWithAllFields() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        when(createService.create(projectId, "x@example.com", "neo", "Neo", "secret123", accountId))
                .thenReturn(details());

        controller.create(
                projectId,
                new CreateProjectUserRequest("x@example.com", "neo", "Neo", "secret123"),
                principal,
                authentication(principal, false));

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(createService).create(projectId, "x@example.com", "neo", "Neo", "secret123", accountId);
    }

    @Test
    void createByNonManagerThrowsPermissionDenied() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        doThrow(new ProjectAccessDeniedException(projectId, accountId))
                .when(projectAccessService).requireManage(projectId, accountId, false);

        assertThatThrownBy(() -> controller.create(
                projectId,
                new CreateProjectUserRequest("x@example.com", null, "Neo", "secret123"),
                principal,
                authentication(principal, false))).isInstanceOf(ProjectAccessDeniedException.class);

        verifyNoInteractions(createService);
    }

    @Test
    void instanceAdminFlagIsPropagatedToAccessService() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;

        controller.list(projectId, principal, authentication(principal, true));

        verify(projectAccessService).requireAccess(projectId, accountId, true);
    }

    @Test
    void statusTransitionsAndDeleteAndResetAllRequireManage() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var auth = authentication(principal, false);

        controller.suspend(projectId, userId, principal, auth);
        controller.reactivate(projectId, userId, principal, auth);
        controller.disable(projectId, userId, principal, auth);
        controller.delete(projectId, userId, principal, auth);
        controller.resetPassword(projectId, userId, new ResetPasswordRequest("newpass123"), principal, auth);

        verify(projectAccessService, org.mockito.Mockito.times(5)).requireManage(projectId, accountId, false);
        verify(statusService).suspend(projectId, userId, accountId);
        verify(statusService).reactivate(projectId, userId, accountId);
        verify(statusService).disable(projectId, userId, accountId);
        verify(deleteService).delete(projectId, userId, accountId);
        verify(resetPasswordService).reset(projectId, userId, "newpass123", accountId);
    }

    private static UsernamePasswordAuthenticationToken authentication(AuthenticatedAccount principal, boolean instanceAdmin) {
        var authorities = instanceAdmin
                ? List.of(new SimpleGrantedAuthority("ROLE_INSTANCE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private static ProjectUserDetails details() {
        return new ProjectUserDetails(
                UUID.randomUUID(), "x@example.com", "neo", "Neo", "ACTIVE", false,
                Instant.parse("2026-01-01T00:00:00Z"), null, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
