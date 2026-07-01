package dev.unzor.nexus.vault.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.vault.api.dto.SecretSummary;
import dev.unzor.nexus.vault.api.dto.SecretValue;
import dev.unzor.nexus.vault.application.service.ProjectVaultService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lectura de secretos del vault desde el API de proyecto ({@code /api/v1/vault}).
 * El {@code projectId} se toma de la API key resuelta. Scope {@code vault:read};
 * cada lectura del valor se audita.
 */
@RestController
@RequestMapping("/api/v1/vault")
class RuntimeVaultController {

    private final ProjectVaultService vaultService;

    RuntimeVaultController(ProjectVaultService vaultService) {
        this.vaultService = vaultService;
    }

    @GetMapping("/secrets")
    @RequiredScope("vault:read")
    List<SecretSummary> list(@AuthenticationPrincipal ResolvedApiKey apiKey) {
        return vaultService.listSecrets(apiKey.projectId());
    }

    @GetMapping("/secrets/{key}")
    @RequiredScope("vault:read")
    SecretValue get(@PathVariable String key, @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return vaultService.readSecretValue(apiKey.projectId(), key);
    }
}
