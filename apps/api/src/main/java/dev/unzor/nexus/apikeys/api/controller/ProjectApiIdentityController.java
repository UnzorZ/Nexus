package dev.unzor.nexus.apikeys.api.controller;

import dev.unzor.nexus.apikeys.api.ScopeFree;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Primer endpoint del API de proyecto ({@code /api/v1/**}). Introspección de la
 * API key resuelta por el filtro; sirve para verificar la autenticación por
 * {@code X-Nexus-Api-Key} end-to-end. No requiere un scope concreto.
 */
@RestController
@RequestMapping("/api/v1")
class ProjectApiIdentityController {

    @GetMapping("/whoami")
    @ScopeFree
    Map<String, Object> whoami(@AuthenticationPrincipal ResolvedApiKey apiKey) {
        return Map.of(
                "projectId", apiKey.projectId(),
                "keyId", apiKey.keyId(),
                "scopes", apiKey.scopes()
        );
    }
}
