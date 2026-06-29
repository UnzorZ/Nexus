package dev.unzor.nexus.modules.api.controller;

import dev.unzor.nexus.modules.api.dto.ProjectModuleStatus;
import dev.unzor.nexus.modules.api.requests.UpdateModuleStateRequest;
import dev.unzor.nexus.modules.application.service.ProjectModuleService;
import dev.unzor.nexus.modules.domain.enums.NexusModule;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/modules")
class ProjectModulesController {

    private final ProjectModuleService projectModuleService;
    private final ProjectAccessService projectAccessService;

    ProjectModulesController(
            ProjectModuleService projectModuleService,
            ProjectAccessService projectAccessService
    ) {
        this.projectModuleService = projectModuleService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<ProjectModuleStatus> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return projectModuleService.listForProject(projectId);
    }

    @PatchMapping("/{moduleKey}")
    ProjectModuleStatus setEnabled(
            @PathVariable UUID projectId,
            @PathVariable String moduleKey,
            @Valid @RequestBody UpdateModuleStateRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return projectModuleService.setEnabled(
                projectId,
                NexusModule.fromKey(moduleKey),
                request.enabled()
        );
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
