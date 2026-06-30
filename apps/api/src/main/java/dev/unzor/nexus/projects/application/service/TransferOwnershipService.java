package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.domain.exception.MembershipAlreadyOwnerException;
import dev.unzor.nexus.projects.domain.exception.MembershipNotActiveException;
import dev.unzor.nexus.projects.domain.exception.MembershipNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso para transferir la propiedad del proyecto a otra membresía activa.
 *
 * <p>Intercambia los roles de forma atómica: la membresía objetivo pasa a ser
 * OWNER y la membresía que actualmente es OWNER pasa a ADMIN. Es la única
 * operación que puede cambiar quién es el propietario, ya que ni la invitación ni
 * el cambio de rol permiten asignar OWNER (validador {@code @NonOwnerRole}).</p>
 */
@Service
public class TransferOwnershipService {

    private final ProjectMembershipRepository membershipRepository;

    public TransferOwnershipService(ProjectMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public void transfer(UUID projectId, UUID targetMembershipId) {
        // SELECT … FOR UPDATE sobre las membresías del proyecto: serializa esta
        // mutación con cualquier otra que afecte al invariante de OWNER (cambio de
        // rol, eliminación), evitando la carrera check-then-act del último owner.
        membershipRepository.findForUpdateByProjectId(projectId);
        ProjectMembership target = membershipRepository
                .findByProjectIdAndId(projectId, targetMembershipId)
                .orElseThrow(() -> new MembershipNotFoundException(targetMembershipId));
        // El destinatario debe estar activo: promover una membresía inactiva
        // dejaría el proyecto con un OWNER sin acceso efectivo.
        if (target.getStatus() != ProjectMembershipStatus.ACTIVE) {
            throw new MembershipNotActiveException(targetMembershipId);
        }
        // Guard esencial: sin él, transferir al propio owner dejaría el proyecto
        // sin ningún OWNER (el objetivo y el owner actual serían la misma fila).
        if (target.getRole() == ProjectMembershipRole.OWNER) {
            throw new MembershipAlreadyOwnerException(targetMembershipId);
        }
        ProjectMembership currentOwner = membershipRepository
                .findAllByProjectIdAndStatus(projectId, ProjectMembershipStatus.ACTIVE).stream()
                .filter(membership -> membership.getRole() == ProjectMembershipRole.OWNER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Project " + projectId + " has no active OWNER to transfer from"));
        target.changeRole(ProjectMembershipRole.OWNER);
        currentOwner.changeRole(ProjectMembershipRole.ADMIN);
        membershipRepository.save(target);
        membershipRepository.save(currentOwner);
    }
}
