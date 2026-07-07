package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.permissions.api.dto.RoleDetails;
import dev.unzor.nexus.permissions.domain.entity.ProjectRole;
import dev.unzor.nexus.permissions.domain.entity.ProjectRolePermission;
import dev.unzor.nexus.permissions.domain.entity.ProjectUserRole;
import dev.unzor.nexus.permissions.domain.exception.RoleNotFoundException;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRolePermissionRepository;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRoleRepository;
import dev.unzor.nexus.permissions.persistence.repository.ProjectUserRoleRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.audit.ProjectUserAuthoritiesChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Asignación de roles a usuarios de proyecto (spec §9.8). La asignación es un
 * reemplazo completo (PUT) — espeja {@code ProjectRolesService.setPermissions}:
 * de-dup, borrado bulk previo y reinserción, para evitar carreras por adición
 * individual y choques con la restricción única
 * {@code (project_user_id, role_id)}.
 *
 * <p>Cada cambio publica {@link ProjectUserAuthoritiesChanged} para que
 * {@code identity} incremente el {@code authz_version} del usuario (invalida
 * snapshots/tokens previos).</p>
 */
@Service
public class ProjectUserRolesService {

    private final ProjectRoleRepository roleRepository;
    private final ProjectRolePermissionRepository rolePermissionRepository;
    private final ProjectUserRoleRepository userRoleRepository;
    private final ProjectLookupService projectLookupService;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectUserRolesService(
            ProjectRoleRepository roleRepository,
            ProjectRolePermissionRepository rolePermissionRepository,
            ProjectUserRoleRepository userRoleRepository,
            ProjectLookupService projectLookupService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.projectLookupService = projectLookupService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Roles asignados a un usuario, con sus claves de permiso (vista de sólo
     * lectura para el panel).
     */
    @Transactional(readOnly = true)
    public List<RoleDetails> rolesForUser(UUID projectId, UUID userId) {
        projectLookupService.requireById(projectId);
        Set<UUID> roleIds = userRoleRepository.findAllByProjectIdAndUserId(projectId, userId).stream()
                .map(ProjectUserRole::getRoleId)
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ProjectRolePermission>> grantsByRole = rolePermissionRepository
                .findAllByProjectId(projectId).stream()
                .filter(grant -> roleIds.contains(grant.getRoleId()))
                .collect(Collectors.groupingBy(ProjectRolePermission::getRoleId));
        return roleRepository.findAllByProjectId(projectId).stream()
                .filter(role -> roleIds.contains(role.getId()))
                .sorted(Comparator.comparing(ProjectRole::getKey))
                .map(role -> RoleDetails.from(role, keysOf(grantsByRole.getOrDefault(role.getId(), List.of()))))
                .toList();
    }

    /**
     * Reemplazo completo (PUT) de los roles asignados al usuario. Valida que cada
     * rol exista en el proyecto, de-duplica preservando el orden, borra las
     * asignaciones previas (bulk DML) y reinserta; audita y publica el evento de
     * cambio de authorities (bump de {@code authz_version}).
     */
    @Transactional
    public List<RoleDetails> setRoles(UUID projectId, UUID userId, List<UUID> roleIds, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(roleIds));

        // Validar que cada rol existe en el proyecto (y resolverlos para la respuesta).
        List<ProjectRole> roles = new ArrayList<>();
        for (UUID roleId : unique) {
            roles.add(roleRepository.findByProjectIdAndId(projectId, roleId)
                    .orElseThrow(() -> new RoleNotFoundException(
                            "Role " + roleId + " not found in project " + projectId + ".")));
        }

        // No-op: si el conjunto solicitado es idéntico al actual, no mutamos ni
        // publicamos el bump — un PUT idempotente no debe invalidar tokens del
        // usuario (authz_version) ni generar ruido de auditoría.
        Set<UUID> current = userRoleRepository.findAllByProjectIdAndUserId(projectId, userId).stream()
                .map(ProjectUserRole::getRoleId)
                .collect(Collectors.toSet());
        if (current.equals(Set.copyOf(unique))) {
            return roleDetailsFor(roles);
        }

        userRoleRepository.deleteByProjectIdAndUserId(projectId, userId);
        for (UUID roleId : unique) {
            userRoleRepository.save(new ProjectUserRole(projectId, userId, roleId));
        }

        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "project_user.roles_set", "project_user", userId.toString(),
                actorAccountId, Map.of("count", unique.size())));
        eventPublisher.publishEvent(new ProjectUserAuthoritiesChanged(projectId, userId));

        return roleDetailsFor(roles);
    }

    private List<RoleDetails> roleDetailsFor(List<ProjectRole> roles) {
        return roles.stream()
                .sorted(Comparator.comparing(ProjectRole::getKey))
                .map(role -> RoleDetails.from(role, keysForRole(role.getId())))
                .toList();
    }

    private List<String> keysForRole(UUID roleId) {
        return keysOf(rolePermissionRepository.findAllByRoleId(roleId));
    }

    private static List<String> keysOf(List<ProjectRolePermission> grants) {
        return grants.stream()
                .map(ProjectRolePermission::getPermissionKey)
                .sorted()
                .toList();
    }
}
