package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.admin.directory.AccountDirectory;
import dev.unzor.nexus.admin.directory.AccountSummary;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InviteMemberServiceTests {

    private final ProjectMembershipRepository membershipRepository = mock(ProjectMembershipRepository.class);
    private final AccountDirectory accountDirectory = mock(AccountDirectory.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final InviteMemberService service =
            new InviteMemberService(membershipRepository, accountDirectory, eventPublisher);

    @Test
    void inviteCreatesActiveMembershipWhenAccountHasNone() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AccountSummary account = new AccountSummary(accountId, "new@example.com", "New", false, false, null);
        when(accountDirectory.findByEmail("new@example.com")).thenReturn(Optional.of(account));
        when(membershipRepository.findByProjectIdAndNexusAccountId(projectId, accountId))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any(ProjectMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.invite(projectId, "new@example.com", ProjectMembershipRole.MEMBER, UUID.randomUUID());

        ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectMembershipRole.MEMBER);
        assertThat(captor.getValue().getStatus()).isEqualTo(ProjectMembershipStatus.ACTIVE);
    }

    @Test
    void inviteReactivatesAndReRolesAnExistingRevokedMembership() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AccountSummary account = new AccountSummary(accountId, "again@example.com", "Again", false, false, null);
        ProjectMembership existing = new ProjectMembership(projectId, accountId, ProjectMembershipRole.MEMBER);
        existing.revoke();
        when(accountDirectory.findByEmail("again@example.com")).thenReturn(Optional.of(account));
        when(membershipRepository.findByProjectIdAndNexusAccountId(projectId, accountId))
                .thenReturn(Optional.of(existing));
        when(membershipRepository.save(any(ProjectMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.invite(projectId, "again@example.com", ProjectMembershipRole.ADMIN, UUID.randomUUID());

        assertThat(existing.getStatus()).isEqualTo(ProjectMembershipStatus.ACTIVE);
        assertThat(existing.getRole()).isEqualTo(ProjectMembershipRole.ADMIN);
    }

    @Test
    void inviteIsSilentNoOpWhenEmailHasNoAccount() {
        UUID projectId = UUID.randomUUID();
        when(accountDirectory.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        // Anti-enumeración: no lanza, no crea membresía, no audita — el endpoint responde
        // 200 OK idéntico al de un invite con cuenta válida, sin revelar la no-existencia.
        service.invite(projectId, "ghost@example.com", ProjectMembershipRole.MEMBER, UUID.randomUUID());

        verify(membershipRepository, never()).save(any(ProjectMembership.class));
        verifyNoInteractions(eventPublisher);
    }
}
