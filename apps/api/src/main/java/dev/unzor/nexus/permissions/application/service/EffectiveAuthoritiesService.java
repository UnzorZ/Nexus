package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.permissions.application.dto.EffectiveAuthorities;
import dev.unzor.nexus.permissions.domain.entity.ProjectRole;
import dev.unzor.nexus.permissions.domain.entity.ProjectRolePermission;
import dev.unzor.nexus.permissions.domain.entity.ProjectUserRole;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRolePermissionRepository;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRoleRepository;
import dev.unzor.nexus.permissions.persistence.repository.ProjectUserRoleRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resuelve las authorities efectivas de un usuario de proyecto: la unión
 * (de-duplicada y ordenada) de las claves de permiso de los roles que tiene
 * asignados. Servicio publicado del módulo {@code permissions} para que
 * {@code identity} construya las authorities del {@code ProjectUser} sin
 * acceder a sus repositorios.
 *
 * <p>Los comodines ({@code orders.*}, {@code *}) se devuelven tal cual; su
 * expansión a claves concretas queda fuera del alcance actual.</p>
 */
@Service
public class EffectiveAuthoritiesService {

    private final ProjectUserRoleRepository userRoleRepository;
    private final ProjectRolePermissionRepository rolePermissionRepository;
    private final ProjectRoleRepository roleRepository;
    private final ProjectLookupService projectLookupService;

    public EffectiveAuthoritiesService(
            ProjectUserRoleRepository userRoleRepository,
            ProjectRolePermissionRepository rolePermissionRepository,
            ProjectRoleRepository roleRepository,
            ProjectLookupService projectLookupService
    ) {
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleRepository = roleRepository;
        this.projectLookupService = projectLookupService;
    }

    /**
     * Claves de permiso efectivas del usuario + las claves de sus roles. Carga las
     * asignaciones del usuario y, si tiene roles, los grants del proyecto filtrando
     * por esos roles en memoria (una sola consulta project-scoped, igual que
     * {@code ProjectRolesService.listForProject}).
     */
    @Transactional(readOnly = true)
    public EffectiveAuthorities forUser(UUID projectId, UUID userId) {
        projectLookupService.requireById(projectId);
        Set<UUID> roleIds = userRoleRepository.findAllByProjectIdAndUserId(projectId, userId).stream()
                .map(ProjectUserRole::getRoleId)
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return EffectiveAuthorities.empty();
        }
        TreeSet<String> keys = rolePermissionRepository.findAllByProjectId(projectId).stream()
                .filter(grant -> roleIds.contains(grant.getRoleId()))
                .map(ProjectRolePermission::getPermissionKey)
                .collect(Collectors.toCollection(TreeSet::new));
        TreeSet<String> roles = roleRepository.findAllByProjectId(projectId).stream()
                .filter(role -> roleIds.contains(role.getId()))
                .map(ProjectRole::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
        return new EffectiveAuthorities(keys, roles);
    }

    /**
     * Usuarios que tienen asignado un rol — usado por el bump transitivo cuando
     * cambian los permisos del rol o se borra (antes del CASCADE).
     */
    @Transactional(readOnly = true)
    public Set<UUID> userIdsForRole(UUID projectId, UUID roleId) {
        return userRoleRepository.findAllByProjectIdAndRoleId(projectId, roleId).stream()
                .map(ProjectUserRole::getUserId)
                .collect(Collectors.toSet());
    }
}
