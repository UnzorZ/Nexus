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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

class ProjectUserRolesServiceTests {

    private final ProjectRoleRepository roleRepository = mock(ProjectRoleRepository.class);
    private final ProjectRolePermissionRepository rolePermissionRepository = mock(ProjectRolePermissionRepository.class);
    private final ProjectUserRoleRepository userRoleRepository = mock(ProjectUserRoleRepository.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProjectUserRolesService service = new ProjectUserRolesService(
            roleRepository, rolePermissionRepository, userRoleRepository, projectLookupService, eventPublisher);

    @Test
    void setRolesReplacesDedupesAuditsAndBumpsAuthzVersion() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        ProjectRole admin = withId(new ProjectRole(projectId, "admin", "Admin", null), adminId);
        ProjectRole viewer = withId(new ProjectRole(projectId, "viewer", "Viewer", null), viewerId);
        when(roleRepository.findByProjectIdAndId(projectId, adminId)).thenReturn(Optional.of(admin));
        when(roleRepository.findByProjectIdAndId(projectId, viewerId)).thenReturn(Optional.of(viewer));
        when(userRoleRepository.save(any(ProjectUserRole.class))).thenAnswer(i -> i.getArgument(0));

        // Duplicate adminId is collapsed; order preserved.
        List<RoleDetails> result = service.setRoles(
                projectId, userId, List.of(adminId, viewerId, adminId), UUID.randomUUID());

        // Bulk delete before re-insert (PUT semantics).
        verify(userRoleRepository).deleteByProjectIdAndUserId(projectId, userId);
        verify(userRoleRepository, times(2)).save(any(ProjectUserRole.class));
        assertThat(result).extracting(RoleDetails::key).containsExactly("admin", "viewer");

        // Audit + the authz_version bump event both published for the user.
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues()).anyMatch(e -> e instanceof ProjectUserAuthoritiesChanged changed
                && changed.projectId().equals(projectId) && changed.userId().equals(userId));
        assertThat(events.getAllValues()).anyMatch(e -> e instanceof AuditEvent);
    }

    @Test
    void setRolesWithEmptyListClearsAssignments() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.setRoles(projectId, userId, List.of(), UUID.randomUUID());

        verify(userRoleRepository).deleteByProjectIdAndUserId(projectId, userId);
        verify(userRoleRepository, never()).save(any(ProjectUserRole.class));
        verify(eventPublisher).publishEvent(any(ProjectUserAuthoritiesChanged.class));
    }

    @Test
    void setRolesRejectsUnknownRole() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID unknownRoleId = UUID.randomUUID();
        when(roleRepository.findByProjectIdAndId(projectId, unknownRoleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setRoles(projectId, userId, List.of(unknownRoleId), UUID.randomUUID()))
                .isInstanceOf(RoleNotFoundException.class);
        verify(userRoleRepository, never()).deleteByProjectIdAndUserId(projectId, userId);
    }

    @Test
    void rolesForUserReturnsAssignedRolesWithKeys() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ProjectRole admin = withId(new ProjectRole(projectId, "admin", "Admin", null), adminId);
        when(userRoleRepository.findAllByProjectIdAndUserId(projectId, userId))
                .thenReturn(List.of(new ProjectUserRole(projectId, userId, adminId)));
        when(roleRepository.findAllByProjectId(projectId)).thenReturn(List.of(admin));
        when(rolePermissionRepository.findAllByProjectId(projectId))
                .thenReturn(List.of(
                        new ProjectRolePermission(projectId, adminId, "orders.*"),
                        new ProjectRolePermission(projectId, UUID.randomUUID(), "other.read")));

        List<RoleDetails> result = service.rolesForUser(projectId, userId);

        assertThat(result).extracting(RoleDetails::key).containsExactly("admin");
        assertThat(result.get(0).permissionKeys()).containsExactly("orders.*");
    }

    @Test
    void rolesForUserWithNoRolesReturnsEmpty() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(userRoleRepository.findAllByProjectIdAndUserId(projectId, userId)).thenReturn(List.of());

        assertThat(service.rolesForUser(projectId, userId)).isEmpty();
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
