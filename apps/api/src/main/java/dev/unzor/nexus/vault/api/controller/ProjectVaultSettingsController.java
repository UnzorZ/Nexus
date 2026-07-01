package dev.unzor.nexus.vault.api.controller;

import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import dev.unzor.nexus.vault.api.dto.MasterKeyReveal;
import dev.unzor.nexus.vault.api.dto.VaultEncryptionInfo;
import dev.unzor.nexus.vault.api.requests.RotateMasterKeyRequest;
import dev.unzor.nexus.vault.application.service.ProjectVaultService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Configuración de cifrado del vault desde el panel: info de cifrado (lectura),
 * revelación y rotación de la master key (escritura, sensible y auditada).
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/vault")
class ProjectVaultSettingsController {

    private final ProjectVaultService vaultService;
    private final ProjectAccessService projectAccessService;

    ProjectVaultSettingsController(ProjectVaultService vaultService, ProjectAccessService projectAccessService) {
        this.vaultService = vaultService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping("/encryption")
    VaultEncryptionInfo encryptionInfo(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return vaultService.encryptionInfo(projectId);
    }

    @GetMapping("/master-key")
    MasterKeyReveal revealMasterKey(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return vaultService.revealMasterKey(projectId, principal.accountId());
    }

    @PostMapping("/master-key")
    VaultEncryptionInfo rotateMasterKey(
            @PathVariable UUID projectId,
            @Valid @RequestBody RotateMasterKeyRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return vaultService.rotateMasterKey(projectId, request.masterKey(), principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
