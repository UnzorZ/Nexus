package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.admin.directory.AccountDirectory;
import dev.unzor.nexus.admin.directory.AccountSummary;
import dev.unzor.nexus.projects.api.dto.MembershipDetails;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.domain.exception.LastOwnerProtectionException;
import dev.unzor.nexus.projects.domain.exception.MembershipNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeMemberRoleServiceTests {

    private final ProjectMembershipRepository membershipRepository = mock(ProjectMembershipRepository.class);
    private final AccountDirectory accountDirectory = mock(AccountDirectory.class);
    private final ChangeMemberRoleService service =
            new ChangeMemberRoleService(membershipRepository, accountDirectory);

    @Test
    void changeRoleUpdatesRole() {
        UUID projectId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership membership = new ProjectMembership(projectId, accountId, ProjectMembershipRole.MEMBER);
        when(membershipRepository.findByProjectIdAndId(projectId, membershipId))
                .thenReturn(Optional.of(membership));
        when(accountDirectory.findById(accountId))
                .thenReturn(Optional.of(new AccountSummary(accountId, "m@example.com", "M", false, null)));
        when(membershipRepository.save(any(ProjectMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MembershipDetails result = service.changeRole(projectId, membershipId, ProjectMembershipRole.ADMIN);

        verify(membershipRepository).findForUpdateByProjectId(projectId);
        assertThat(membership.getRole()).isEqualTo(ProjectMembershipRole.ADMIN);
        assertThat(result.role()).isEqualTo(ProjectMembershipRole.ADMIN);
    }

    @Test
    void changeRoleBlocksDemotingTheLastOwner() {
        UUID projectId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership owner = new ProjectMembership(projectId, accountId, ProjectMembershipRole.OWNER);
        when(membershipRepository.findByProjectIdAndId(projectId, membershipId)).thenReturn(Optional.of(owner));
        when(membershipRepository.countByProjectIdAndRoleAndStatus(
                projectId, ProjectMembershipRole.OWNER, ProjectMembershipStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(projectId, membershipId, ProjectMembershipRole.ADMIN))
                .isInstanceOf(LastOwnerProtectionException.class);
        assertThat(owner.getRole()).isEqualTo(ProjectMembershipRole.OWNER);
        verify(membershipRepository, never()).save(any(ProjectMembership.class));
    }

    @Test
    void changeRoleAllowsDemotingAnOwnerWhenOthersRemain() {
        UUID projectId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership owner = new ProjectMembership(projectId, accountId, ProjectMembershipRole.OWNER);
        when(membershipRepository.findByProjectIdAndId(projectId, membershipId)).thenReturn(Optional.of(owner));
        when(membershipRepository.countByProjectIdAndRoleAndStatus(
                projectId, ProjectMembershipRole.OWNER, ProjectMembershipStatus.ACTIVE)).thenReturn(2L);
        when(accountDirectory.findById(accountId))
                .thenReturn(Optional.of(new AccountSummary(accountId, "o@example.com", "O", false, null)));
        when(membershipRepository.save(any(ProjectMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.changeRole(projectId, membershipId, ProjectMembershipRole.ADMIN);

        assertThat(owner.getRole()).isEqualTo(ProjectMembershipRole.ADMIN);
    }

    @Test
    void changeRoleThrowsWhenMembershipMissing() {
        UUID projectId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(membershipRepository.findByProjectIdAndId(projectId, membershipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(projectId, membershipId, ProjectMembershipRole.ADMIN))
                .isInstanceOf(MembershipNotFoundException.class);
    }
}
