package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectSummary;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso para listar los proyectos a los que una cuenta Nexus tiene
 * acceso. Los administradores de instancia ven todos los proyectos; el resto
 * solo aquellos en los que son miembros activos.
 */
@Service
public class ListAccessibleProjectsService {

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;

    public ListAccessibleProjectsService(
            ProjectRepository projectRepository,
            ProjectMembershipRepository membershipRepository
    ) {
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> listAccessible(UUID accountId, boolean isInstanceAdmin) {
        if (isInstanceAdmin) {
            return projectRepository.findAll().stream()
                    .map(ProjectSummary::from)
                    .sorted(BY_NAME)
                    .toList();
        }

        List<UUID> projectIds = membershipRepository
                .findAllByNexusAccountIdAndStatus(accountId, ProjectMembershipStatus.ACTIVE)
                .stream()
                .map(ProjectMembership::getProjectId)
                .distinct()
                .toList();

        if (projectIds.isEmpty()) {
            return List.of();
        }

        // Una única consulta en lugar de N findById (uno por membresía), y orden
        // determinista por nombre: findAllById no garantiza el orden de los ids.
        return projectRepository.findAllById(projectIds).stream()
                .map(ProjectSummary::from)
                .sorted(BY_NAME)
                .toList();
    }

    /** Orden determinista y estable para los listados de proyectos. */
    private static final Comparator<ProjectSummary> BY_NAME =
            Comparator.comparing(ProjectSummary::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(ProjectSummary::slug);
}
