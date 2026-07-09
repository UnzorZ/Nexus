package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.admin.directory.AccountDirectory;
import dev.unzor.nexus.admin.directory.AccountSummary;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso para añadir (o reactivar) la membresía de una cuenta en un proyecto.
 *
 * <p>Si la cuenta ya tiene una membresía (p. ej. revocada o suspendida), se
 * reactiva y se actualiza su rol en lugar de crear una fila nueva, evitando
 * violar la restricción única {@code uk_project_membership_project_account}. Las
 * nuevas membresías nacen activas.</p>
 *
 * <p><b>Anti-enumeración:</b> si ninguna {@link AccountSummary} corresponde al email,
 * el caso de uso es un <b>no-op silencioso</b> — no crea nada, no lanza, no audita.
 * Así la respuesta del endpoint es idéntica exista o no la cuenta, y un administrador
 * no puede distinguir un email con cuenta de uno sin ella por el resultado del invite.
 * El único efecto residual (un miembro real sí aparece en el listado tras re-fetch) es
 * estado legítimo que el admin debe poder ver; cerrar esa vía requeriría un flujo de
 * invitación por email (invite de quien aún no tiene cuenta), fuera de alcance aquí.</p>
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
    public void invite(UUID projectId, String email, ProjectMembershipRole role, UUID actorAccountId) {
        Optional<AccountSummary> account = accountDirectory.findByEmail(email);
        if (account.isEmpty()) {
            // No revelamos si la cuenta existe: respuesta idéntica a un invite válido.
            return;
        }
        UUID accountId = account.get().id();
        ProjectMembership membership = membershipRepository
                .findByProjectIdAndNexusAccountId(projectId, accountId)
                .orElseGet(() -> new ProjectMembership(projectId, accountId, role));
        // Reactivamos miembros previamente revocados/suspendidos y aplicamos el rol
        // solicitado de forma uniforme (para una fila nueva estas llamaciones son
        // redundantes pero inofensivas, ya que el constructor ya fija ACTIVE + role).
        membership.activate();
        membership.changeRole(role);
        ProjectMembership saved = membershipRepository.save(membership);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "member.invited", "member", Objects.toString(saved.getId(), null),
                actorAccountId,
                Map.of("account", accountId.toString(), "role", role.name())));
    }
}
