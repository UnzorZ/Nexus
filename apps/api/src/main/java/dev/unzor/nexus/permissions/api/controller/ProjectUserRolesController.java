package dev.unzor.nexus.permissions.api.controller;

import dev.unzor.nexus.permissions.api.dto.RoleDetails;
import dev.unzor.nexus.permissions.api.requests.SetUserRolesRequest;
import dev.unzor.nexus.permissions.application.service.ProjectUserRolesService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Roles asignados a un usuario de proyecto. Subrecurso anidado bajo la raíz de
 * usuarios (propiedad del módulo {@code identity}), pero servido desde
 * {@code permissions} porque la lógica de roles vive aquí — mismo patrón que
 * {@code /roles/{roleId}/permissions}. Lectura {@code requireAccess}; escritura
 * {@code requireManage}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/users/{userId}/roles")
class ProjectUserRolesController {

    private final ProjectUserRolesService userRolesService;
    private final ProjectAccessService projectAccessService;

    ProjectUserRolesController(
            ProjectUserRolesService userRolesService,
            ProjectAccessService projectAccessService
    ) {
        this.userRolesService = userRolesService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<RoleDetails> list(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return userRolesService.rolesForUser(projectId, userId);
    }

    @PutMapping
    List<RoleDetails> setRoles(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @Valid @RequestBody SetUserRolesRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return userRolesService.setRoles(projectId, userId, request.roleIds(), principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
