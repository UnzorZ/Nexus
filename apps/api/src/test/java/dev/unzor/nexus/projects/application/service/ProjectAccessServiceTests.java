package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectAccessServiceTests {

    private final ProjectMembershipRepository membershipRepository = mock(ProjectMembershipRepository.class);
    private final ProjectAccessService service = new ProjectAccessService(membershipRepository);

    @Test
    void instanceAdminCanManageAndDelete() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        assertThat(service.canManage(projectId, accountId, true)).isTrue();
        assertThat(service.canDelete(projectId, accountId, true)).isTrue();
    }

    @Test
    void ownerCanManageAndDelete() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership membership = new ProjectMembership(projectId, accountId, ProjectMembershipRole.OWNER);
        when(membershipRepository.findByProjectIdAndNexusAccountId(projectId, accountId))
                .thenReturn(Optional.of(membership));

        assertThat(service.canManage(projectId, accountId, false)).isTrue();
        assertThat(service.canDelete(projectId, accountId, false)).isTrue();
    }

    @Test
    void adminCanManageButNotDelete() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership membership = new ProjectMembership(projectId, accountId, ProjectMembershipRole.ADMIN);
        when(membershipRepository.findByProjectIdAndNexusAccountId(projectId, accountId))
                .thenReturn(Optional.of(membership));

        assertThat(service.canManage(projectId, accountId, false)).isTrue();
        assertThat(service.canDelete(projectId, accountId, false)).isFalse();
    }

    @Test
    void memberCannotManageOrDelete() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership membership = new ProjectMembership(projectId, accountId, ProjectMembershipRole.MEMBER);
        when(membershipRepository.findByProjectIdAndNexusAccountId(projectId, accountId))
                .thenReturn(Optional.of(membership));

        assertThat(service.canManage(projectId, accountId, false)).isFalse();
        assertThat(service.canDelete(projectId, accountId, false)).isFalse();
    }

    @Test
    void suspendedMembershipDoesNotGrantManage() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership membership = new ProjectMembership(projectId, accountId, ProjectMembershipRole.OWNER);
        membership.suspend();
        when(membershipRepository.findByProjectIdAndNexusAccountId(projectId, accountId))
                .thenReturn(Optional.of(membership));

        assertThat(service.canManage(projectId, accountId, false)).isFalse();
        assertThat(service.canDelete(projectId, accountId, false)).isFalse();
    }

    @Test
    void requireManageThrowsForMember() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProjectMembership membership = new ProjectMembership(projectId, accountId, ProjectMembershipRole.MEMBER);
        when(membershipRepository.findByProjectIdAndNexusAccountId(projectId, accountId))
                .thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.requireManage(projectId, accountId, false))
                .isInstanceOf(ProjectAccessDeniedException.class);
    }
}
