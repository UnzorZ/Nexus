package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.permissions.api.dto.RoleDetails;
import dev.unzor.nexus.permissions.domain.entity.ProjectRole;
import dev.unzor.nexus.permissions.domain.entity.ProjectRolePermission;
import dev.unzor.nexus.permissions.domain.exception.RoleAlreadyExistsException;
import dev.unzor.nexus.permissions.domain.exception.RoleNotFoundException;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRolePermissionRepository;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRoleRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.audit.ProjectUserAuthoritiesChanged;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Casos de uso de los roles de un proyecto (spec §9.8). Un rol agrupa claves de
 * permiso; la asignación es un reemplazo completo (PUT) para evitar carreras
 * por adición individual.
 */
@Service
public class ProjectRolesService {

    private final ProjectRoleRepository roleRepository;
    private final ProjectRolePermissionRepository rolePermissionRepository;
    private final ProjectLookupService projectLookupService;
    private final ApplicationEventPublisher eventPublisher;
    private final EffectiveAuthoritiesService effectiveAuthoritiesService;

    public ProjectRolesService(
            ProjectRoleRepository roleRepository,
            ProjectRolePermissionRepository rolePermissionRepository,
            ProjectLookupService projectLookupService,
            ApplicationEventPublisher eventPublisher,
            EffectiveAuthoritiesService effectiveAuthoritiesService
    ) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.projectLookupService = projectLookupService;
        this.eventPublisher = eventPublisher;
        this.effectiveAuthoritiesService = effectiveAuthoritiesService;
    }

    @Transactional(readOnly = true)
    public List<RoleDetails> listForProject(UUID projectId) {
        projectLookupService.requireById(projectId);

        Map<UUID, List<ProjectRolePermission>> grantsByRole = rolePermissionRepository
                .findAllByProjectId(projectId).stream()
                .collect(Collectors.groupingBy(ProjectRolePermission::getRoleId));

        return roleRepository.findAllByProjectId(projectId).stream()
                .sorted(Comparator.comparing(ProjectRole::getKey))
                .map(role -> RoleDetails.from(role, keysOf(grantsByRole.getOrDefault(role.getId(), List.of()))))
                .toList();
    }

    @Transactional
    public RoleDetails create(UUID projectId, String key, String label, String description, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        if (roleRepository.existsByProjectIdAndKey(projectId, key)) {
            throw new RoleAlreadyExistsException(
                    "A role with key '" + key + "' already exists in this project.");
        }
        try {
            // saveAndFlush materializa el INSERT dentro del bloque para que la
            // violación de unicidad (carrera de creación concurrente) se lance
            // aquí y se traduzca a 409, no como 500 al hacer commit.
            ProjectRole saved = roleRepository.saveAndFlush(
                    new ProjectRole(projectId, key, label, description));
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "role.created", "role", Objects.toString(saved.getId(), null),
                    actorAccountId, Map.of("key", key)));
            return RoleDetails.from(saved, List.of());
        } catch (DataIntegrityViolationException exception) {
            if (isKeyUniqueViolation(exception)) {
                throw new RoleAlreadyExistsException(
                        "A role with key '" + key + "' already exists in this project.");
            }
            throw exception;
        }
    }

    /** Restricciones de unicidad de la clave del rol, para distinguirlas de
     * otras violaciones de integridad que no son un conflicto de key. */
    private static final Set<String> KEY_UNIQUE_CONSTRAINTS =
            Set.of("uk_project_roles_project_key");

    private static boolean isKeyUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && KEY_UNIQUE_CONSTRAINTS.contains(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Transactional
    public RoleDetails update(UUID projectId, UUID roleId, String label, String description, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectRole role = requireRole(projectId, roleId);
        role.relabel(label, description);
        roleRepository.save(role);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "role.updated", "role", roleId.toString(),
                actorAccountId, Map.of("key", role.getKey())));
        return RoleDetails.from(role, keysForRole(roleId));
    }

    @Transactional
    public void delete(UUID projectId, UUID roleId, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectRole role = requireRole(projectId, roleId);
        // Capturar los asignatarios ANTES del delete: el ON DELETE CASCADE del FK
        // (role_id) elimina también las filas de project_user_roles, así que hay
        // que resolverlos antes para poder bumpar su authz_version.
        Set<UUID> affectedUsers = effectiveAuthoritiesService.userIdsForRole(projectId, roleId);
        roleRepository.delete(role);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "role.deleted", "role", roleId.toString(),
                actorAccountId, Map.of("key", role.getKey())));
        // project_role_permissions y project_user_roles se eliminan vía ON DELETE CASCADE.
        bumpAffectedUsers(projectId, affectedUsers);
    }

    /**
     * Reemplazo completo (PUT) de las claves de permiso del rol. De-duplica
     * preservando el orden; el borrado-previo dentro de la misma transacción
     * evita cualquier conflicto de unicidad con el conjunto anterior.
     */
    @Transactional
    public RoleDetails setPermissions(UUID projectId, UUID roleId, List<String> permissionKeys,
                                      UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectRole role = requireRole(projectId, roleId);
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(permissionKeys));
        rolePermissionRepository.deleteByRoleId(roleId);
        for (String key : unique) {
            rolePermissionRepository.save(new ProjectRolePermission(projectId, roleId, key));
        }
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "role.permissions_set", "role", roleId.toString(),
                actorAccountId, Map.of("count", unique.size())));
        // Cambiar los permisos del rol cambia las authorities efectivas de todos
        // sus asignatarios: bump transitivo de su authz_version.
        bumpAffectedUsers(projectId, effectiveAuthoritiesService.userIdsForRole(projectId, roleId));
        return RoleDetails.from(role, unique);
    }

    /**
     * Publica un {@link ProjectUserAuthoritiesChanged} por cada usuario afectado
     * para que {@code identity} incremente su {@code authz_version} (invalida
     * snapshots/tokens previos). Fan-out síncrono dentro de la misma tx;
     * aceptable para cargas de administración (señalado como costura de
     * escalabilidad si un rol tuviera miles de asignatarios).
     */
    private void bumpAffectedUsers(UUID projectId, Set<UUID> userIds) {
        for (UUID userId : userIds) {
            eventPublisher.publishEvent(new ProjectUserAuthoritiesChanged(projectId, userId));
        }
    }

    private ProjectRole requireRole(UUID projectId, UUID roleId) {
        return roleRepository.findByProjectIdAndId(projectId, roleId)
                .orElseThrow(() -> new RoleNotFoundException(
                        "Role " + roleId + " not found in project " + projectId + "."));
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
