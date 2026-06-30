package dev.unzor.nexus.permissions.api.controller;

import dev.unzor.nexus.permissions.api.dto.RoleDetails;
import dev.unzor.nexus.permissions.api.requests.CreateRoleRequest;
import dev.unzor.nexus.permissions.api.requests.SetRolePermissionsRequest;
import dev.unzor.nexus.permissions.api.requests.UpdateRoleRequest;
import dev.unzor.nexus.permissions.application.service.ProjectRolesService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Roles de un proyecto: crea, lista, reetiqueta y elimina roles, y reemplaza el
 * conjunto de claves de permiso de cada rol. Las escrituras requieren
 * {@code requireManage}; la lectura, {@code requireAccess}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/roles")
class ProjectRolesController {

    private final ProjectRolesService rolesService;
    private final ProjectAccessService projectAccessService;

    ProjectRolesController(
            ProjectRolesService rolesService,
            ProjectAccessService projectAccessService
    ) {
        this.rolesService = rolesService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<RoleDetails> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return rolesService.listForProject(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RoleDetails create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return rolesService.create(projectId, request.key(), request.label(), request.description(),
                principal.accountId());
    }

    @PatchMapping("/{roleId}")
    RoleDetails update(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return rolesService.update(projectId, roleId, request.label(), request.description(),
                principal.accountId());
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        rolesService.delete(projectId, roleId, principal.accountId());
    }

    @PutMapping("/{roleId}/permissions")
    RoleDetails setPermissions(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @Valid @RequestBody SetRolePermissionsRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return rolesService.setPermissions(projectId, roleId, request.permissionKeys(), principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
