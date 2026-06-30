package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.admin.directory.AccountDirectory;
import dev.unzor.nexus.admin.directory.AccountSummary;
import dev.unzor.nexus.projects.api.dto.MembershipDetails;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListProjectMembersServiceTests {

    private final ProjectMembershipRepository membershipRepository = mock(ProjectMembershipRepository.class);
    private final AccountDirectory accountDirectory = mock(AccountDirectory.class);
    private final ListProjectMembersService service =
            new ListProjectMembersService(membershipRepository, accountDirectory);

    @Test
    void listReturnsActiveMembersEnrichedWithAccountData() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        ProjectMembership owner = new ProjectMembership(projectId, ownerId, ProjectMembershipRole.OWNER);
        ProjectMembership member = new ProjectMembership(projectId, memberId, ProjectMembershipRole.MEMBER);

        when(membershipRepository.findAllByProjectIdAndStatus(projectId, ProjectMembershipStatus.ACTIVE))
                .thenReturn(List.of(owner, member));
        when(accountDirectory.findAllById(anySet())).thenReturn(Map.of(
                ownerId, new AccountSummary(ownerId, "owner@example.com", "Owner", true, false, Instant.parse("2026-01-01T00:00:00Z")),
                memberId, new AccountSummary(memberId, "member@example.com", "Member", false, false, null)
        ));

        List<MembershipDetails> result = service.list(projectId);

        assertThat(result).hasSize(2);
        assertThat(result).satisfiesExactlyInAnyOrder(
                details -> {
                    assertThat(details.role()).isEqualTo(ProjectMembershipRole.OWNER);
                    assertThat(details.email()).isEqualTo("owner@example.com");
                    assertThat(details.mfaEnabled()).isTrue();
                },
                details -> {
                    assertThat(details.role()).isEqualTo(ProjectMembershipRole.MEMBER);
                    assertThat(details.email()).isEqualTo("member@example.com");
                    assertThat(details.lastActiveAt()).isNull();
                }
        );
    }

    @Test
    void listReturnsEmptyAndSkipsAccountLookupWhenNoActiveMembers() {
        UUID projectId = UUID.randomUUID();
        when(membershipRepository.findAllByProjectIdAndStatus(projectId, ProjectMembershipStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(service.list(projectId)).isEmpty();
        verifyNoInteractions(accountDirectory);
    }
}
