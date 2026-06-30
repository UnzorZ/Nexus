package dev.unzor.nexus.apikeys.api.controller;

import dev.unzor.nexus.apikeys.api.dto.ApiKeyCreated;
import dev.unzor.nexus.apikeys.api.dto.ApiKeySummary;
import dev.unzor.nexus.apikeys.api.requests.CreateApiKeyRequest;
import dev.unzor.nexus.apikeys.api.requests.UpdateApiKeyRequest;
import dev.unzor.nexus.apikeys.application.service.ProjectApiKeysService;
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
 * Gestión de API keys de un proyecto (panel). Las escrituras requieren
 * {@code requireManage}; la lectura, {@code requireAccess}. El secreto completo
 * solo se devuelve al crear y al rotar.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/api-keys")
class ProjectApiKeysController {

    private final ProjectApiKeysService service;
    private final ProjectAccessService projectAccessService;

    ProjectApiKeysController(ProjectApiKeysService service, ProjectAccessService projectAccessService) {
        this.service = service;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<ApiKeySummary> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return service.listForProject(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiKeyCreated create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateApiKeyRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return service.create(projectId, request.name(), request.scopes(), request.expiresAt(), principal.accountId());
    }

    @PatchMapping("/{keyId}")
    ApiKeySummary update(
            @PathVariable UUID projectId,
            @PathVariable UUID keyId,
            @Valid @RequestBody UpdateApiKeyRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return service.update(projectId, keyId, request.name(), request.status(), request.expiresAt(),
                principal.accountId());
    }

    @PostMapping("/{keyId}/rotate")
    ApiKeyCreated rotate(
            @PathVariable UUID projectId,
            @PathVariable UUID keyId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return service.rotate(projectId, keyId, principal.accountId());
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID keyId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        service.delete(projectId, keyId, principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
