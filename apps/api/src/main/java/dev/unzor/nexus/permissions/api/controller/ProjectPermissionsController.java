package dev.unzor.nexus.permissions.api.controller;

import dev.unzor.nexus.permissions.api.dto.PermissionDetails;
import dev.unzor.nexus.permissions.api.requests.CreatePermissionRequest;
import dev.unzor.nexus.permissions.api.requests.UpdatePermissionRequest;
import dev.unzor.nexus.permissions.application.service.ProjectPermissionsService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Catálogo de permisos de un proyecto: declara, lista, reetiqueta y elimina
 * permisos (claves definidas por el usuario). Las escrituras requieren
 * {@code requireManage}; la lectura, {@code requireAccess}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/permissions")
class ProjectPermissionsController {

    private final ProjectPermissionsService permissionsService;
    private final ProjectAccessService projectAccessService;

    ProjectPermissionsController(
            ProjectPermissionsService permissionsService,
            ProjectAccessService projectAccessService
    ) {
        this.permissionsService = permissionsService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<PermissionDetails> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return permissionsService.listForProject(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PermissionDetails create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreatePermissionRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return permissionsService.create(projectId, request.key(), request.label(), request.description(),
                principal.accountId());
    }

    @PatchMapping("/{permissionId}")
    PermissionDetails update(
            @PathVariable UUID projectId,
            @PathVariable UUID permissionId,
            @Valid @RequestBody UpdatePermissionRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return permissionsService.update(projectId, permissionId, request.label(), request.description(),
                principal.accountId());
    }

    @DeleteMapping("/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID permissionId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        permissionsService.delete(projectId, permissionId, principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
