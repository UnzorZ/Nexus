package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.api.dto.OauthClientCreated;
import dev.unzor.nexus.identity.api.dto.OauthClientSummary;
import dev.unzor.nexus.identity.api.requests.CreateOauthClientRequest;
import dev.unzor.nexus.identity.api.requests.UpdateOauthClientRequest;
import dev.unzor.nexus.identity.application.service.ProjectOauthClientsService;
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
 * Gestión panel de clientes OAuth de un proyecto (spec §9.6). Lecturas
 * {@code requireAccess}; mutaciones {@code requireManage}; el secreto sólo se
 * devuelve al crear/rotar. Espejea {@code ProjectApiKeysController}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/oauth-clients")
class ProjectOauthClientsController {

    private final ProjectOauthClientsService service;
    private final ProjectAccessService projectAccessService;

    ProjectOauthClientsController(ProjectOauthClientsService service, ProjectAccessService projectAccessService) {
        this.service = service;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<OauthClientSummary> list(
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
    OauthClientCreated create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateOauthClientRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return service.create(
                projectId, request.name(), request.redirectUris(), request.postLogoutRedirectUris(),
                request.grantTypes(), request.scopes(), request.requirePkce(), request.confidential(),
                request.consentRequired(), principal.accountId());
    }

    @PatchMapping("/{id}")
    OauthClientSummary update(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOauthClientRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return service.update(
                projectId, id, request.name(), request.redirectUris(),
                request.postLogoutRedirectUris(), request.scopes(), request.status(), principal.accountId());
    }

    @PostMapping("/{id}/rotate")
    OauthClientCreated rotate(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return service.rotateSecret(projectId, id, principal.accountId());
    }

    @PostMapping("/{id}/disable")
    OauthClientSummary disable(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return service.disable(projectId, id, principal.accountId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        service.delete(projectId, id, principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
