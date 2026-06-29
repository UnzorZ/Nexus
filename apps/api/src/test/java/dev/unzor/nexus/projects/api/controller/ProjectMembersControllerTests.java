package dev.unzor.nexus.projects.api.controller;

import dev.unzor.nexus.projects.api.dto.MembershipDetails;
import dev.unzor.nexus.projects.api.requests.InviteMemberRequest;
import dev.unzor.nexus.projects.api.requests.UpdateMemberRoleRequest;
import dev.unzor.nexus.projects.application.service.ChangeMemberRoleService;
import dev.unzor.nexus.projects.application.service.InviteMemberService;
import dev.unzor.nexus.projects.application.service.ListProjectMembersService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.projects.application.service.RemoveMemberService;
import dev.unzor.nexus.projects.application.service.TransferOwnershipService;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
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

class ProjectMembersControllerTests {

    private final ListProjectMembersService listService = mock(ListProjectMembersService.class);
    private final InviteMemberService inviteService = mock(InviteMemberService.class);
    private final ChangeMemberRoleService changeRoleService = mock(ChangeMemberRoleService.class);
    private final RemoveMemberService removeService = mock(RemoveMemberService.class);
    private final TransferOwnershipService transferOwnershipService = mock(TransferOwnershipService.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final ProjectMembersController controller =
            new ProjectMembersController(listService, inviteService, changeRoleService, removeService, transferOwnershipService, projectAccessService);

    @Test
    void listDelegatesAfterRequireAccess() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = authentication(principal);
        when(listService.list(projectId)).thenReturn(List.of(details()));

        controller.list(projectId, principal, authentication);

        verify(projectAccessService).requireAccess(projectId, accountId, false);
        verify(listService).list(projectId);
    }

    @Test
    void inviteByNonManagerThrowsPermissionDenied() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = authentication(principal);
        doThrow(new ProjectAccessDeniedException(projectId, accountId))
                .when(projectAccessService).requireManage(projectId, accountId, false);

        assertThatThrownBy(() -> controller.invite(
                projectId,
                new InviteMemberRequest("x@example.com", ProjectMembershipRole.MEMBER),
                principal,
                authentication
        )).isInstanceOf(ProjectAccessDeniedException.class);

        verifyNoInteractions(inviteService);
    }

    @Test
    void inviteByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = authentication(principal);
        when(inviteService.invite(projectId, "x@example.com", ProjectMembershipRole.MEMBER))
                .thenReturn(details());

        controller.invite(
                projectId,
                new InviteMemberRequest("x@example.com", ProjectMembershipRole.MEMBER),
                principal,
                authentication
        );

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(inviteService).invite(projectId, "x@example.com", ProjectMembershipRole.MEMBER);
    }

    @Test
    void changeRoleByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = authentication(principal);
        when(changeRoleService.changeRole(projectId, membershipId, ProjectMembershipRole.ADMIN))
                .thenReturn(details());

        controller.changeRole(
                projectId,
                membershipId,
                new UpdateMemberRoleRequest(ProjectMembershipRole.ADMIN),
                principal,
                authentication
        );

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(changeRoleService).changeRole(projectId, membershipId, ProjectMembershipRole.ADMIN);
    }

    @Test
    void removeByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = authentication(principal);

        controller.remove(projectId, membershipId, principal, authentication);

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(removeService).remove(projectId, membershipId);
    }

    @Test
    void transferByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = authentication(principal);

        controller.transferOwnership(projectId, memberId, principal, authentication);

        verify(projectAccessService).requireDelete(projectId, accountId, false);
        verify(transferOwnershipService).transfer(projectId, memberId);
    }

    @Test
    void transferByNonOwnerThrowsPermissionDenied() {
        UUID projectId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = authentication(principal);
        doThrow(new ProjectAccessDeniedException(projectId, accountId))
                .when(projectAccessService).requireDelete(projectId, accountId, false);

        assertThatThrownBy(() -> controller.transferOwnership(projectId, memberId, principal, authentication))
                .isInstanceOf(ProjectAccessDeniedException.class);
        verifyNoInteractions(transferOwnershipService);
    }

    private static UsernamePasswordAuthenticationToken authentication(AuthenticatedAccount principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static MembershipDetails details() {
        return new MembershipDetails(
                UUID.randomUUID(), UUID.randomUUID(), "x@example.com", "X",
                ProjectMembershipRole.MEMBER, ProjectMembershipStatus.ACTIVE,
                false, null, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
