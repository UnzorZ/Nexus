package dev.unzor.nexus.projects.application.service;

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

class RemoveMemberServiceTests {

    private final ProjectMembershipRepository membershipRepository = mock(ProjectMembershipRepository.class);
    private final RemoveMemberService service = new RemoveMemberService(membershipRepository);

    @Test
    void removeRevokesMembership() {
        UUID projectId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership membership = new ProjectMembership(projectId, accountId, ProjectMembershipRole.MEMBER);
        when(membershipRepository.findByProjectIdAndId(projectId, membershipId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(ProjectMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.remove(projectId, membershipId);

        verify(membershipRepository).findForUpdateByProjectId(projectId);
        assertThat(membership.getStatus()).isEqualTo(ProjectMembershipStatus.REVOKED);
    }

    @Test
    void removeBlocksTheLastActiveOwner() {
        UUID projectId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership owner = new ProjectMembership(projectId, accountId, ProjectMembershipRole.OWNER);
        when(membershipRepository.findByProjectIdAndId(projectId, membershipId)).thenReturn(Optional.of(owner));
        when(membershipRepository.countByProjectIdAndRoleAndStatus(
                projectId, ProjectMembershipRole.OWNER, ProjectMembershipStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.remove(projectId, membershipId))
                .isInstanceOf(LastOwnerProtectionException.class);
        assertThat(owner.getStatus()).isEqualTo(ProjectMembershipStatus.ACTIVE);
        verify(membershipRepository, never()).save(any(ProjectMembership.class));
    }

    @Test
    void removeIsIdempotentForAnAlreadyRevokedOwner() {
        UUID projectId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership owner = new ProjectMembership(projectId, accountId, ProjectMembershipRole.OWNER);
        owner.revoke();
        when(membershipRepository.findByProjectIdAndId(projectId, membershipId)).thenReturn(Optional.of(owner));

        service.remove(projectId, membershipId);

        assertThat(owner.getStatus()).isEqualTo(ProjectMembershipStatus.REVOKED);
    }

    @Test
    void removeThrowsWhenMembershipMissing() {
        UUID projectId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(membershipRepository.findByProjectIdAndId(projectId, membershipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(projectId, membershipId))
                .isInstanceOf(MembershipNotFoundException.class);
    }
}
