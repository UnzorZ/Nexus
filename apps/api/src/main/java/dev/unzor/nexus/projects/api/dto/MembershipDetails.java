package dev.unzor.nexus.projects.api.dto;

import dev.unzor.nexus.admin.directory.AccountSummary;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de una membresía enriquecida con los datos públicos de la cuenta, sin
 * exponer las entidades JPA.
 */
public record MembershipDetails(
        UUID id,
        UUID accountId,
        String email,
        String displayName,
        ProjectMembershipRole role,
        ProjectMembershipStatus status,
        boolean mfaEnabled,
        Instant lastActiveAt,
        Instant createdAt
) {

    public static MembershipDetails from(ProjectMembership membership, AccountSummary account) {
        return new MembershipDetails(
                membership.getId(),
                membership.getNexusAccountId(),
                account.email(),
                account.displayName(),
                membership.getRole(),
                membership.getStatus(),
                account.mfaEnabled(),
                account.lastLoginAt(),
                membership.getCreatedAt()
        );
    }
}
