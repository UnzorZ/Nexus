package dev.unzor.nexus.registry.api.controller;

import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.registry.api.dto.HeartbeatInstanceView;
import dev.unzor.nexus.registry.application.service.RegistryHeartbeatService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Listado de latidos de un proyecto para el panel (spec §13.1). Solo lectura:
 * las instancias se crean desde el endpoint de runtime. Requiere
 * {@code requireAccess} (cualquier miembro del proyecto puede verlo).
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

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
