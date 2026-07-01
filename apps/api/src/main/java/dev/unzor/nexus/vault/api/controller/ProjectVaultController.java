package dev.unzor.nexus.vault.api.controller;

import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import dev.unzor.nexus.vault.api.dto.SecretSummary;
import dev.unzor.nexus.vault.api.dto.SecretValue;
import dev.unzor.nexus.vault.api.requests.WriteSecretRequest;
import dev.unzor.nexus.vault.application.service.ProjectVaultService;
import dev.unzor.nexus.vault.domain.enums.VaultCipher;
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
 * Gestión de secretos del vault desde el panel. El valor plano nunca se
 * devuelve (sólo metadatos). Escrituras con {@code requireManage}; lectura de
 * metadatos con {@code requireAccess}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/vault/secrets")
class ProjectVaultController {

    private final ProjectVaultService vaultService;
    private final ProjectAccessService projectAccessService;

    ProjectVaultController(ProjectVaultService vaultService, ProjectAccessService projectAccessService) {
        this.vaultService = vaultService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<SecretSummary> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return vaultService.listSecrets(projectId);
    }

    @PostMapping("/{key}")
    @ResponseStatus(HttpStatus.CREATED)
    SecretSummary create(
            @PathVariable UUID projectId,
            @PathVariable String key,
            @Valid @RequestBody WriteSecretRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return vaultService.createSecret(projectId, key, request.value(),
                VaultCipher.fromKey(request.cipher()), principal.accountId());
    }

    @PatchMapping("/{key}")
    SecretSummary rotate(
            @PathVariable UUID projectId,
            @PathVariable String key,
            @Valid @RequestBody WriteSecretRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return vaultService.rotateSecret(projectId, key, request.value(),
                VaultCipher.fromKey(request.cipher()), principal.accountId());
    }

    /** Revela el valor descifrado de un secreto desde el panel (Manage, auditado). */
    @GetMapping("/{key}/value")
    SecretValue revealValue(
            @PathVariable UUID projectId,
            @PathVariable String key,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return vaultService.revealSecretValue(projectId, key, principal.accountId());
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
        vaultService.deleteSecret(projectId, key, principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
