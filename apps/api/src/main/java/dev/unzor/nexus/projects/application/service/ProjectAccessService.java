package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.springframework.stereotype.Service;

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
}
