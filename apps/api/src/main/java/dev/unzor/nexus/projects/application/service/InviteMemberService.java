package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.admin.directory.AccountDirectory;
import dev.unzor.nexus.admin.directory.AccountSummary;
import dev.unzor.nexus.projects.api.dto.MembershipDetails;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.exception.UnknownAccountException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para añadir (o reactivar) la membresía de una cuenta en un proyecto.
 *
 * <p>Si la cuenta ya tiene una membresía (p. ej. revocada o suspendida), se
 * reactiva y se actualiza su rol en lugar de crear una fila nueva, evitando
 * violar la restricción única {@code uk_project_membership_project_account}. Las
 * nuevas membresías nacen activas.</p>
 */
@Service
public class InviteMemberService {

    private final ProjectMembershipRepository membershipRepository;
    private final AccountDirectory accountDirectory;
    private final ApplicationEventPublisher eventPublisher;

    public InviteMemberService(
            ProjectMembershipRepository membershipRepository,
            AccountDirectory accountDirectory,
            ApplicationEventPublisher eventPublisher
    ) {
        this.membershipRepository = membershipRepository;
        this.accountDirectory = accountDirectory;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MembershipDetails invite(UUID projectId, String email, ProjectMembershipRole role, UUID actorAccountId) {
        AccountSummary account = accountDirectory.findByEmail(email)
                .orElseThrow(() -> new UnknownAccountException(email));

        ProjectMembership membership = membershipRepository
                .findByProjectIdAndNexusAccountId(projectId, account.id())
                .orElseGet(() -> new ProjectMembership(projectId, account.id(), role));
        // Reactivamos miembros previamente revocados/suspendidos y aplicamos el rol
        // solicitado de forma uniforme (para una fila nueva estas llamaciones son
        // redundantes pero inofensivas, ya que el constructor ya fija ACTIVE + role).
        membership.activate();
        membership.changeRole(role);
        ProjectMembership saved = membershipRepository.save(membership);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "member.invited", "member", Objects.toString(saved.getId(), null),
                actorAccountId,
                Map.of("account", account.id().toString(), "role", role.name())));
        return MembershipDetails.from(saved, account);
    }
}
