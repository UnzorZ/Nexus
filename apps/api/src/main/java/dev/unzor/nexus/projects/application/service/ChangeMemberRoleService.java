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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso para cambiar el rol de una membresía, impidiendo dejar al proyecto
 * sin ningún OWNER activo.
 */
@Service
public class ChangeMemberRoleService {

    private final ProjectMembershipRepository membershipRepository;
    private final AccountDirectory accountDirectory;

    public ChangeMemberRoleService(
            ProjectMembershipRepository membershipRepository,
            AccountDirectory accountDirectory
    ) {
        this.membershipRepository = membershipRepository;
        this.accountDirectory = accountDirectory;
    }

    @Transactional
    public MembershipDetails changeRole(UUID projectId, UUID membershipId, ProjectMembershipRole newRole) {
        ProjectMembership membership = membershipRepository
                .findByProjectIdAndId(projectId, membershipId)
                .orElseThrow(() -> new MembershipNotFoundException(membershipId));
        // Degradar al único OWNER activo dejaría el proyecto sin propietario.
        if (membership.getRole() == ProjectMembershipRole.OWNER
                && newRole != ProjectMembershipRole.OWNER
                && activeOwnerCount(projectId) <= 1) {
            throw new LastOwnerProtectionException(projectId);
        }
        membership.changeRole(newRole);
        ProjectMembership saved = membershipRepository.save(membership);
        AccountSummary account = accountDirectory.findById(saved.getNexusAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "Account " + saved.getNexusAccountId() + " missing for membership " + saved.getId()));
        return MembershipDetails.from(saved, account);
    }

    private long activeOwnerCount(UUID projectId) {
        return membershipRepository.countByProjectIdAndRoleAndStatus(
                projectId, ProjectMembershipRole.OWNER, ProjectMembershipStatus.ACTIVE);
    }
}
