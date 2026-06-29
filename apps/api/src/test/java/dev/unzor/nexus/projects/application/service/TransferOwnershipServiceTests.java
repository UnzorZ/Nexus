package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.domain.exception.MembershipAlreadyOwnerException;
import dev.unzor.nexus.projects.domain.exception.MembershipNotActiveException;
import dev.unzor.nexus.projects.domain.exception.MembershipNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferOwnershipServiceTests {

    private final ProjectMembershipRepository membershipRepository = mock(ProjectMembershipRepository.class);
    private final TransferOwnershipService service = new TransferOwnershipService(membershipRepository);

    @Test
    void transferPromotesTargetAndDemotesCurrentOwner() {
        UUID projectId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ProjectMembership target = new ProjectMembership(projectId, targetId, ProjectMembershipRole.MEMBER);
        ProjectMembership owner = new ProjectMembership(projectId, ownerId, ProjectMembershipRole.OWNER);
        when(membershipRepository.findByProjectIdAndId(projectId, targetId)).thenReturn(Optional.of(target));
        when(membershipRepository.findAllByProjectIdAndStatus(projectId, ProjectMembershipStatus.ACTIVE))
                .thenReturn(List.of(owner, target));
        when(membershipRepository.save(any(ProjectMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.transfer(projectId, targetId);

        assertThat(target.getRole()).isEqualTo(ProjectMembershipRole.OWNER);
        assertThat(owner.getRole()).isEqualTo(ProjectMembershipRole.ADMIN);
        verify(membershipRepository).findForUpdateByProjectId(projectId);
        verify(membershipRepository).save(target);
        verify(membershipRepository).save(owner);
    }

    @Test
    void transferThrowsWhenTargetMembershipMissing() {
        UUID projectId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(membershipRepository.findByProjectIdAndId(projectId, targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(projectId, targetId))
                .isInstanceOf(MembershipNotFoundException.class);
        verify(membershipRepository, never()).save(any(ProjectMembership.class));
    }

    @Test
    void transferThrowsWhenTargetIsAlreadyOwner() {
        UUID projectId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ProjectMembership target = new ProjectMembership(projectId, targetId, ProjectMembershipRole.OWNER);
        when(membershipRepository.findByProjectIdAndId(projectId, targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.transfer(projectId, targetId))
                .isInstanceOf(MembershipAlreadyOwnerException.class);
        verify(membershipRepository, never()).save(any(ProjectMembership.class));
    }

    @Test
    void transferThrowsWhenTargetIsInactive() {
        UUID projectId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ProjectMembership target = new ProjectMembership(projectId, targetId, ProjectMembershipRole.MEMBER);
        target.suspend();
        when(membershipRepository.findByProjectIdAndId(projectId, targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.transfer(projectId, targetId))
                .isInstanceOf(MembershipNotActiveException.class);
        verify(membershipRepository, never()).save(any(ProjectMembership.class));
    }
}
