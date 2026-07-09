package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.permissions.application.dto.EffectiveAuthorities;
import dev.unzor.nexus.permissions.domain.entity.ProjectRole;
import dev.unzor.nexus.permissions.domain.entity.ProjectRolePermission;
import dev.unzor.nexus.permissions.domain.entity.ProjectUserRole;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRolePermissionRepository;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRoleRepository;
import dev.unzor.nexus.permissions.persistence.repository.ProjectUserRoleRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EffectiveAuthoritiesServiceTest {

    private final ProjectUserRoleRepository userRoleRepository = mock(ProjectUserRoleRepository.class);
    private final ProjectRolePermissionRepository rolePermissionRepository = mock(ProjectRolePermissionRepository.class);
    private final ProjectRoleRepository roleRepository = mock(ProjectRoleRepository.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final EffectiveAuthoritiesService service = new EffectiveAuthoritiesService(
            userRoleRepository, rolePermissionRepository, roleRepository, projectLookupService);

    @Test
    void forUserUnionsDedupesAndSortsKeysAcrossRolesWithWildcardsVerbatim() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID supportId = UUID.randomUUID();
        when(userRoleRepository.findAllByProjectIdAndUserId(projectId, userId)).thenReturn(List.of(
                new ProjectUserRole(projectId, userId, adminId),
                new ProjectUserRole(projectId, userId, supportId)));
        // Grants are project-scoped; service filters by the user's role ids in memory.
        when(rolePermissionRepository.findAllByProjectId(projectId)).thenReturn(List.of(
                new ProjectRolePermission(projectId, adminId, "orders.cancel"),
                new ProjectRolePermission(projectId, adminId, "orders.*"),     // wildcard survives
                new ProjectRolePermission(projectId, supportId, "orders.read"), // overlaps orders.* (kept as-is)
                new ProjectRolePermission(projectId, UUID.randomUUID(), "other.x"))); // another role's grant, excluded
        // Pre-computamos los mocks de rol (getId/getKey) fuera del when(...) del repo:
        ProjectRole adminRole = role(adminId, "admin");
        ProjectRole supportRole = role(supportId, "support");
        when(roleRepository.findAllByProjectId(projectId)).thenReturn(List.of(adminRole, supportRole));

        EffectiveAuthorities result = service.forUser(projectId, userId);

        // Sorted (TreeSet), de-duplicated, wildcards verbatim — no expansion.
        assertThat(result.permissionKeys()).containsExactly("orders.*", "orders.cancel", "orders.read");
        assertThat(result.roleKeys()).containsExactly("admin", "support");
    }

    @Test
    void forUserWithNoRolesReturnsEmpty() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(userRoleRepository.findAllByProjectIdAndUserId(projectId, userId)).thenReturn(List.of());

        assertThat(service.forUser(projectId, userId).permissionKeys()).isEmpty();
    }

    @Test
    void userIdsForRoleReturnsAssignees() {
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        when(userRoleRepository.findAllByProjectIdAndRoleId(projectId, roleId)).thenReturn(List.of(
                new ProjectUserRole(projectId, u1, roleId),
                new ProjectUserRole(projectId, u2, roleId)));

        assertThat(service.userIdsForRole(projectId, roleId)).containsExactlyInAnyOrder(u1, u2);
    }

    /** ProjectRole.id es @GeneratedValue (no fijable por constructor): mockeamos id+key. */
    private static ProjectRole role(UUID id, String key) {
        ProjectRole role = mock(ProjectRole.class);
        when(role.getId()).thenReturn(id);
        when(role.getKey()).thenReturn(key);
        return role;
    }
}
