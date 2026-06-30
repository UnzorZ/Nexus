package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.domain.exception.LastOwnerProtectionException;
import dev.unzor.nexus.projects.domain.exception.MembershipNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso para revocar (eliminar de forma reversible) la membresía de una
 * cuenta, impidiendo dejar al proyecto sin ningún OWNER activo.
 */
@Service
public class RemoveMemberService {

    private final ProjectMembershipRepository membershipRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RemoveMemberService(ProjectMembershipRepository membershipRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.membershipRepository = membershipRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void remove(UUID projectId, UUID membershipId, UUID actorAccountId) {
        // SELECT … FOR UPDATE sobre las membresías del proyecto: serializa esta
        // mutación con cualquier otra que afecte al invariante de OWNER, evitando
        // la carrera check-then-act del recuento de owners activos.
        membershipRepository.findForUpdateByProjectId(projectId);
        ProjectMembership membership = membershipRepository
                .findByProjectIdAndId(projectId, membershipId)
                .orElseThrow(() -> new MembershipNotFoundException(membershipId));
        // Solo un OWNER activo cuenta para la protección: revocar de nuevo una
        // membresía ya no activa es idempotente y no reduce propietarios activos.
        if (membership.getRole() == ProjectMembershipRole.OWNER
                && membership.getStatus() == ProjectMembershipStatus.ACTIVE
                && activeOwnerCount(projectId) <= 1) {
            throw new LastOwnerProtectionException(projectId);
        }
        membership.revoke();
        membershipRepository.save(membership);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "member.removed", "member", membershipId.toString(),
                actorAccountId, null));
    }

    private long activeOwnerCount(UUID projectId) {
        return membershipRepository.countByProjectIdAndRoleAndStatus(
                projectId, ProjectMembershipRole.OWNER, ProjectMembershipStatus.ACTIVE);
    }
}
