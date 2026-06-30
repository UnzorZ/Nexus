package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.permissions.api.dto.RoleDetails;
import dev.unzor.nexus.permissions.domain.entity.ProjectRole;
import dev.unzor.nexus.permissions.domain.entity.ProjectRolePermission;
import dev.unzor.nexus.permissions.domain.exception.RoleAlreadyExistsException;
import dev.unzor.nexus.permissions.domain.exception.RoleNotFoundException;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRolePermissionRepository;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRoleRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Casos de uso de los roles de un proyecto (spec §9.8). Un rol agrupa claves de
 * permiso; la asignación es un reemplazo completo (PUT) para evitar carreras
 * por adición individual.
 */
@Service
public class ProjectRolesService {

    private final ProjectRoleRepository roleRepository;
    private final ProjectRolePermissionRepository rolePermissionRepository;
    private final ProjectLookupService projectLookupService;

    public ProjectRolesService(
            ProjectRoleRepository roleRepository,
            ProjectRolePermissionRepository rolePermissionRepository,
            ProjectLookupService projectLookupService
    ) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.projectLookupService = projectLookupService;
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
    public RoleDetails create(UUID projectId, String key, String label, String description) {
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
    public RoleDetails update(UUID projectId, UUID roleId, String label, String description) {
        projectLookupService.requireById(projectId);
        ProjectRole role = requireRole(projectId, roleId);
        role.relabel(label, description);
        roleRepository.save(role);
        return RoleDetails.from(role, keysForRole(roleId));
    }

    @Transactional
    public void delete(UUID projectId, UUID roleId) {
        projectLookupService.requireById(projectId);
        ProjectRole role = requireRole(projectId, roleId);
        roleRepository.delete(role);
        // project_role_permissions se elimina vía el ON DELETE CASCADE del FK.
    }

    /**
     * Reemplazo completo (PUT) de las claves de permiso del rol. De-duplica
     * preservando el orden; el borrado-previo dentro de la misma transacción
     * evita cualquier conflicto de unicidad con el conjunto anterior.
     */
    @Transactional
    public RoleDetails setPermissions(UUID projectId, UUID roleId, List<String> permissionKeys) {
        projectLookupService.requireById(projectId);
        ProjectRole role = requireRole(projectId, roleId);
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(permissionKeys));
        rolePermissionRepository.deleteByRoleId(roleId);
        for (String key : unique) {
            rolePermissionRepository.save(new ProjectRolePermission(projectId, roleId, key));
        }
        return RoleDetails.from(role, unique);
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
