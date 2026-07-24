package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.api.dto.GoogleIdpSummary;
import dev.unzor.nexus.identity.api.requests.SaveGoogleIdpRequest;
import dev.unzor.nexus.identity.application.service.ProjectOidcIdpService;
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

import java.util.UUID;

/**
 * Panel management of the per-project Google IdP configuration. Reads require
 * {@code requireAccess}; writes (save, delete) require {@code requireManage}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/federation/google")
class ProjectOidcIdpController {

    private final ProjectOidcIdpService idpService;
    private final ProjectAccessService projectAccessService;

    ProjectOidcIdpController(ProjectOidcIdpService idpService, ProjectAccessService projectAccessService) {
        this.idpService = idpService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    GoogleIdpSummary get(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean instanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), instanceAdmin);
        return idpService.find(projectId);
    }

    @PutMapping
    GoogleIdpSummary save(
            @PathVariable UUID projectId,
            @Valid @RequestBody SaveGoogleIdpRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean instanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), instanceAdmin);
        return idpService.save(projectId, request, principal.accountId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean instanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), instanceAdmin);
        idpService.delete(projectId, principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
