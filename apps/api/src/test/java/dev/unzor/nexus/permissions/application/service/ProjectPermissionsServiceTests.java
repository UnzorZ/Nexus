package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.permissions.api.dto.PermissionDetails;
import dev.unzor.nexus.permissions.domain.entity.ProjectPermission;
import dev.unzor.nexus.permissions.domain.exception.PermissionAlreadyExistsException;
import dev.unzor.nexus.permissions.domain.exception.PermissionNotFoundException;
import dev.unzor.nexus.permissions.persistence.repository.ProjectPermissionRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectPermissionsServiceTests {

    private final ProjectPermissionRepository permissionRepository = mock(ProjectPermissionRepository.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final ProjectPermissionsService service =
            new ProjectPermissionsService(permissionRepository, projectLookupService);

    @Test
    void listReturnsPermissionsForProject() {
        UUID projectId = UUID.randomUUID();
        ProjectPermission permission = new ProjectPermission(projectId, "orders.cancel", "Cancel", "desc");
        when(permissionRepository.findAllByProjectId(projectId)).thenReturn(List.of(permission));

        List<PermissionDetails> result = service.listForProject(projectId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("orders.cancel");
        assertThat(result.get(0).source().name()).isEqualTo("WEB");
    }

    @Test
    void createPersistsPermission() {
        UUID projectId = UUID.randomUUID();
        when(permissionRepository.existsByProjectIdAndKey(projectId, "orders.cancel")).thenReturn(false);
        when(permissionRepository.saveAndFlush(any(ProjectPermission.class))).thenAnswer(i -> i.getArgument(0));

        PermissionDetails result = service.create(projectId, "orders.cancel", "Cancel", "desc");

        assertThat(result.key()).isEqualTo("orders.cancel");
        verify(permissionRepository).saveAndFlush(any(ProjectPermission.class));
    }

    @Test
    void createTranslatesConcurrentUniqueRaceToConflict() {
        UUID projectId = UUID.randomUUID();
        when(permissionRepository.existsByProjectIdAndKey(projectId, "orders.cancel")).thenReturn(false);
        var violation = new org.hibernate.exception.ConstraintViolationException(
                "unique", new java.sql.SQLException("dup"), "uk_project_permissions_project_key");
        when(permissionRepository.saveAndFlush(any(ProjectPermission.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("unique", violation));

        assertThatThrownBy(() -> service.create(projectId, "orders.cancel", "Cancel", null))
                .isInstanceOf(PermissionAlreadyExistsException.class);
    }

    @Test
    void createRejectsDuplicateKey() {
        UUID projectId = UUID.randomUUID();
        when(permissionRepository.existsByProjectIdAndKey(projectId, "orders.cancel")).thenReturn(true);

        assertThatThrownBy(() -> service.create(projectId, "orders.cancel", "Cancel", "desc"))
                .isInstanceOf(PermissionAlreadyExistsException.class);
        verify(permissionRepository, never()).save(any(ProjectPermission.class));
    }

    @Test
    void updateRelabelsPermission() {
        UUID projectId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        ProjectPermission permission = withId(new ProjectPermission(projectId, "orders.cancel", "Cancel", "old"), permissionId);
        when(permissionRepository.findByProjectIdAndId(projectId, permissionId)).thenReturn(Optional.of(permission));
        when(permissionRepository.save(any(ProjectPermission.class))).thenAnswer(i -> i.getArgument(0));

        PermissionDetails result = service.update(projectId, permissionId, "Cancel order", "new");

        assertThat(result.label()).isEqualTo("Cancel order");
        assertThat(result.description()).isEqualTo("new");
        assertThat(result.key()).isEqualTo("orders.cancel");
    }

    @Test
    void updateThrowsWhenPermissionMissing() {
        UUID projectId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        when(permissionRepository.findByProjectIdAndId(projectId, permissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(projectId, permissionId, "x", null))
                .isInstanceOf(PermissionNotFoundException.class);
    }

    @Test
    void deleteRemovesPermission() {
        UUID projectId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        ProjectPermission permission = withId(new ProjectPermission(projectId, "orders.cancel", "Cancel", null), permissionId);
        when(permissionRepository.findByProjectIdAndId(projectId, permissionId)).thenReturn(Optional.of(permission));

        service.delete(projectId, permissionId);

        verify(permissionRepository).delete(permission);
    }

    /** Fija el id generado por JPA sin persistir, para los tests de unidad. */
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
