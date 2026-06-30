package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.permissions.api.dto.RoleDetails;
import dev.unzor.nexus.permissions.domain.entity.ProjectRole;
import dev.unzor.nexus.permissions.domain.entity.ProjectRolePermission;
import dev.unzor.nexus.permissions.domain.exception.RoleAlreadyExistsException;
import dev.unzor.nexus.permissions.domain.exception.RoleNotFoundException;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRolePermissionRepository;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRoleRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectRolesServiceTests {

    private final ProjectRoleRepository roleRepository = mock(ProjectRoleRepository.class);
    private final ProjectRolePermissionRepository rolePermissionRepository = mock(ProjectRolePermissionRepository.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final ProjectRolesService service =
            new ProjectRolesService(roleRepository, rolePermissionRepository, projectLookupService,
                    mock(ApplicationEventPublisher.class));

    @Test
    void listReturnsRolesGroupedWithTheirKeys() {
        UUID projectId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        ProjectRole admin = withId(new ProjectRole(projectId, "admin", "Admin", null), adminId);
        ProjectRole viewer = withId(new ProjectRole(projectId, "viewer", "Viewer", null), viewerId);
        when(roleRepository.findAllByProjectId(projectId)).thenReturn(List.of(viewer, admin));
        when(rolePermissionRepository.findAllByProjectId(projectId)).thenReturn(List.of(
                new ProjectRolePermission(projectId, adminId, "orders.*"),
                new ProjectRolePermission(projectId, viewerId, "orders.read")
        ));

        List<RoleDetails> result = service.listForProject(projectId);

        assertThat(result).extracting(RoleDetails::key).containsExactly("admin", "viewer");
        assertThat(result.get(0).permissionKeys()).containsExactly("orders.*");
        assertThat(result.get(1).permissionKeys()).containsExactly("orders.read");
    }

    @Test
    void createPersistsRoleWithoutPermissions() {
        UUID projectId = UUID.randomUUID();
        when(roleRepository.existsByProjectIdAndKey(projectId, "admin")).thenReturn(false);
        when(roleRepository.saveAndFlush(any(ProjectRole.class))).thenAnswer(i -> i.getArgument(0));

        RoleDetails result = service.create(projectId, "admin", "Admin", "desc", UUID.randomUUID());

        assertThat(result.key()).isEqualTo("admin");
        assertThat(result.system()).isFalse();
        assertThat(result.permissionKeys()).isEmpty();
    }

    @Test
    void createTranslatesConcurrentUniqueRaceToConflict() {
        UUID projectId = UUID.randomUUID();
        when(roleRepository.existsByProjectIdAndKey(projectId, "admin")).thenReturn(false);
        var violation = new org.hibernate.exception.ConstraintViolationException(
                "unique", new java.sql.SQLException("dup"), "uk_project_roles_project_key");
        when(roleRepository.saveAndFlush(any(ProjectRole.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("unique", violation));

        assertThatThrownBy(() -> service.create(projectId, "admin", "Admin", null, UUID.randomUUID()))
                .isInstanceOf(RoleAlreadyExistsException.class);
    }

    @Test
    void createRejectsDuplicateKey() {
        UUID projectId = UUID.randomUUID();
        when(roleRepository.existsByProjectIdAndKey(projectId, "admin")).thenReturn(true);

        assertThatThrownBy(() -> service.create(projectId, "admin", "Admin", null, UUID.randomUUID()))
                .isInstanceOf(RoleAlreadyExistsException.class);
        verify(roleRepository, never()).save(any(ProjectRole.class));
    }

    @Test
    void setPermissionsReplacesAndDedupes() {
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        ProjectRole role = withId(new ProjectRole(projectId, "admin", "Admin", null), roleId);
        when(roleRepository.findByProjectIdAndId(projectId, roleId)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.save(any(ProjectRolePermission.class))).thenAnswer(i -> i.getArgument(0));

        RoleDetails result = service.setPermissions(
                projectId, roleId, List.of("orders.cancel", "orders.*", "orders.cancel"),
                UUID.randomUUID());

        verify(rolePermissionRepository).deleteByRoleId(roleId);
        verify(rolePermissionRepository, times(2)).save(any(ProjectRolePermission.class));
        assertThat(result.permissionKeys()).containsExactly("orders.cancel", "orders.*");
    }

    @Test
    void setPermissionsThrowsWhenRoleMissing() {
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(roleRepository.findByProjectIdAndId(projectId, roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPermissions(projectId, roleId, List.of("orders.*"), UUID.randomUUID()))
                .isInstanceOf(RoleNotFoundException.class);
        verify(rolePermissionRepository, never()).deleteByRoleId(roleId);
    }

    @Test
    void deleteRemovesRoleAndCascadesGrants() {
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        ProjectRole role = withId(new ProjectRole(projectId, "admin", "Admin", null), roleId);
        when(roleRepository.findByProjectIdAndId(projectId, roleId)).thenReturn(Optional.of(role));

        service.delete(projectId, roleId, UUID.randomUUID());

        verify(roleRepository).delete(role);
        // No borramos los grants a mano: el FK ON DELETE CASCADE lo hace la BD.
        verify(rolePermissionRepository, never()).deleteByRoleId(roleId);
    }

    @SuppressWarnings("unchecked")
    private static <T> T withId(T entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
