package dev.unzor.nexus.registry.api.controller;

import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.registry.api.dto.HeartbeatInstanceView;
import dev.unzor.nexus.registry.api.dto.RegistrySettings;
import dev.unzor.nexus.registry.api.requests.SaveRegistrySettingsRequest;
import dev.unzor.nexus.registry.application.service.RegistryHeartbeatService;
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
 * Listado de latidos de un proyecto para el panel (spec §13.1) y configuración
 * de los umbrales de liveness. Las instancias se crean desde el endpoint de
 * runtime. Requiere {@code requireAccess} para leer; {@code requireManage} para
 * cambiar los umbrales.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/heartbeats")
class ProjectRegistryController {

    private final RegistryHeartbeatService service;
    private final ProjectAccessService projectAccessService;

    ProjectRegistryController(RegistryHeartbeatService service, ProjectAccessService projectAccessService) {
        this.service = service;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<HeartbeatInstanceView> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin(authentication));
        return service.listForProject(projectId);
    }

    @GetMapping("/settings")
    RegistrySettings getSettings(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin(authentication));
        return service.getSettings(projectId);
    }

    @PutMapping("/settings")
    RegistrySettings saveSettings(
            @PathVariable UUID projectId,
            @Valid @RequestBody SaveRegistrySettingsRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin(authentication));
        return service.saveSettings(projectId, request.intervalSeconds(),
                request.timeoutSeconds(), request.offlineNotifyEnabled(),
                request.offlineNotifyEmail(), principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
