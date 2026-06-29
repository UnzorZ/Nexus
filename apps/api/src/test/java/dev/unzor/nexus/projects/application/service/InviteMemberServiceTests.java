package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.admin.directory.AccountDirectory;
import dev.unzor.nexus.admin.directory.AccountSummary;
import dev.unzor.nexus.projects.api.dto.MembershipDetails;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.domain.exception.UnknownAccountException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InviteMemberServiceTests {

    private final ProjectMembershipRepository membershipRepository = mock(ProjectMembershipRepository.class);
    private final AccountDirectory accountDirectory = mock(AccountDirectory.class);
    private final InviteMemberService service =
            new InviteMemberService(membershipRepository, accountDirectory);

    @Test
    void inviteCreatesActiveMembershipWhenAccountHasNone() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AccountSummary account = new AccountSummary(accountId, "new@example.com", "New", false, null);
        when(accountDirectory.findByEmail("new@example.com")).thenReturn(Optional.of(account));
        when(membershipRepository.findByProjectIdAndNexusAccountId(projectId, accountId))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any(ProjectMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MembershipDetails result = service.invite(projectId, "new@example.com", ProjectMembershipRole.MEMBER);

        ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectMembershipRole.MEMBER);
        assertThat(captor.getValue().getStatus()).isEqualTo(ProjectMembershipStatus.ACTIVE);
        assertThat(result.email()).isEqualTo("new@example.com");
    }

    @Test
    void inviteReactivatesAndReRolesAnExistingRevokedMembership() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AccountSummary account = new AccountSummary(accountId, "again@example.com", "Again", false, null);
        ProjectMembership existing = new ProjectMembership(projectId, accountId, ProjectMembershipRole.MEMBER);
        existing.revoke();
        when(accountDirectory.findByEmail("again@example.com")).thenReturn(Optional.of(account));
        when(membershipRepository.findByProjectIdAndNexusAccountId(projectId, accountId))
                .thenReturn(Optional.of(existing));
        when(membershipRepository.save(any(ProjectMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MembershipDetails result = service.invite(projectId, "again@example.com", ProjectMembershipRole.ADMIN);

        assertThat(existing.getStatus()).isEqualTo(ProjectMembershipStatus.ACTIVE);
        assertThat(existing.getRole()).isEqualTo(ProjectMembershipRole.ADMIN);
        assertThat(result.status()).isEqualTo(ProjectMembershipStatus.ACTIVE);
    }

    @Test
    void inviteThrowsWhenEmailHasNoAccount() {
        UUID projectId = UUID.randomUUID();
        when(accountDirectory.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(projectId, "ghost@example.com", ProjectMembershipRole.MEMBER))
                .isInstanceOf(UnknownAccountException.class);
        verify(membershipRepository, never()).save(any(ProjectMembership.class));
    }
}
