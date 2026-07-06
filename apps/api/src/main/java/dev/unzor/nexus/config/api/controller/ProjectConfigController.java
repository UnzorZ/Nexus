package dev.unzor.nexus.config.api.controller;

import dev.unzor.nexus.config.api.dto.ConfigValueSummary;
import dev.unzor.nexus.config.api.requests.SetConfigValueRequest;
import dev.unzor.nexus.config.application.service.ProjectConfigService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Gestión de la configuración de un proyecto desde el panel. La lectura requiere
 * {@code requireAccess}; las escrituras (upsert, delete), {@code requireManage}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/config")
class ProjectConfigController {

    private final ProjectConfigService configService;
    private final ProjectAccessService projectAccessService;

    ProjectConfigController(ProjectConfigService configService, ProjectAccessService projectAccessService) {
        this.configService = configService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<ConfigValueSummary> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return configService.listForProject(projectId);
    }

    @PutMapping("/{key}")
    ConfigValueSummary upsert(
            @PathVariable UUID projectId,
            @PathVariable String key,
            @Valid @RequestBody SetConfigValueRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return configService.upsert(projectId, key, request.value(), request.valueType(), principal.accountId());
    }

    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID projectId,
            @PathVariable String key,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        configService.delete(projectId, key, principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
