package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Primitiva de autorización compartida por todos los endpoints de proyecto.
 *
 * <p>Comprueba que la cuenta autenticada tenga una membresía activa en el
 * proyecto, o bien el privilegio global de administración de instancia.</p>
 */
@Service
public class ProjectAccessService {

    private final ProjectMembershipRepository membershipRepository;

    public ProjectAccessService(ProjectMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    /**
     * Verifica que {@code accountId} tenga acceso al proyecto indicado.
     *
     * @throws ProjectAccessDeniedException si no tiene acceso.
     */
    public void requireAccess(UUID projectId, UUID accountId, boolean isInstanceAdmin) {
        if (isInstanceAdmin) {
            return;
        }
        boolean hasMembership = membershipRepository
                .existsByProjectIdAndNexusAccountIdAndStatus(
                        projectId, accountId, ProjectMembershipStatus.ACTIVE
                );
        if (!hasMembership) {
            throw new ProjectAccessDeniedException(projectId, accountId);
        }
    }

    /**
     * Verifica que {@code accountId} pueda gestionar la configuración del proyecto.
     *
     * @throws ProjectAccessDeniedException si no tiene permisos suficientes.
     */
    public void requireManage(UUID projectId, UUID accountId, boolean isInstanceAdmin) {
        if (!canManage(projectId, accountId, isInstanceAdmin)) {
            throw new ProjectAccessDeniedException(projectId, accountId);
        }
    }

    /**
     * Verifica que {@code accountId} pueda archivar el proyecto.
     *
     * @throws ProjectAccessDeniedException si no tiene permisos suficientes.
     */
    public void requireDelete(UUID projectId, UUID accountId, boolean isInstanceAdmin) {
        if (!canDelete(projectId, accountId, isInstanceAdmin)) {
            throw new ProjectAccessDeniedException(projectId, accountId);
        }
    }

    /**
     * Indica si la cuenta puede editar metadatos del proyecto.
     */
    public boolean canManage(UUID projectId, UUID accountId, boolean isInstanceAdmin) {
        if (isInstanceAdmin) {
            return true;
        }
        return findActiveMembership(projectId, accountId)
                .map(membership -> membership.getRole() == ProjectMembershipRole.OWNER
                        || membership.getRole() == ProjectMembershipRole.ADMIN)
                .orElse(false);
    }

    /**
     * Indica si la cuenta puede archivar el proyecto.
     */
    public boolean canDelete(UUID projectId, UUID accountId, boolean isInstanceAdmin) {
        if (isInstanceAdmin) {
            return true;
        }
        return findActiveMembership(projectId, accountId)
                .map(membership -> membership.getRole() == ProjectMembershipRole.OWNER)
                .orElse(false);
    }

    private Optional<ProjectMembership> findActiveMembership(UUID projectId, UUID accountId) {
        return membershipRepository.findByProjectIdAndNexusAccountId(projectId, accountId)
                .filter(membership -> membership.getStatus() == ProjectMembershipStatus.ACTIVE);
    }
}
