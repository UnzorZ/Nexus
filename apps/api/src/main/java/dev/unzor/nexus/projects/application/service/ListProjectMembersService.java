package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.admin.directory.AccountDirectory;
import dev.unzor.nexus.admin.directory.AccountSummary;
import dev.unzor.nexus.projects.api.dto.MembershipDetails;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipStatus;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caso de uso para listar las membresías activas de un proyecto, enriquecidas
 * con los datos públicos de cada cuenta en una sola consulta.
 */
@Service
public class ListProjectMembersService {

    private final ProjectMembershipRepository membershipRepository;
    private final AccountDirectory accountDirectory;

    public ListProjectMembersService(
            ProjectMembershipRepository membershipRepository,
            AccountDirectory accountDirectory
    ) {
        this.membershipRepository = membershipRepository;
        this.accountDirectory = accountDirectory;
    }

    @Transactional(readOnly = true)
    public List<MembershipDetails> list(UUID projectId) {
        List<ProjectMembership> memberships = membershipRepository
                .findAllByProjectIdAndStatus(projectId, ProjectMembershipStatus.ACTIVE);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Set<UUID> accountIds = memberships.stream()
                .map(ProjectMembership::getNexusAccountId)
                .collect(Collectors.toSet());
        Map<UUID, AccountSummary> accounts = accountDirectory.findAllById(accountIds);
        // Una membresía sin cuenta es anómala (no hay borrado duro de cuentas); se
        // omite para no emitir una vista con datos de cuenta ausentes.
        return memberships.stream()
                .filter(membership -> accounts.containsKey(membership.getNexusAccountId()))
                .map(membership -> MembershipDetails.from(
                        membership,
                        accounts.get(membership.getNexusAccountId())))
                .toList();
    }
}
