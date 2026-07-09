package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.identity.api.dto.AuthorizationSnapshot;
import dev.unzor.nexus.identity.application.service.AuthorizationSnapshotService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API de proyecto para autorización (máquina, {@code /api/v1/**}, API-key).
 * Expone el <b>snapshot de autorización</b> (spec §14.11) que el SDK de Nexus
 * (spec §18) cachea para resolver permisos localmente.
 *
 * <p>El {@code projectId} se toma del API key resuelto (no de la URL); la cadena
 * {@code /api/v1/**} y el {@code RequiredScopeInterceptor} se encargan de auth +
 * scope ({@code authz:snapshot}).</p>
 */
@RestController
@RequestMapping("/api/v1/authz")
class RuntimeAuthorizationController {

    private final AuthorizationSnapshotService snapshotService;

    RuntimeAuthorizationController(AuthorizationSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/users/{userId}/snapshot")
    @RequiredScope("authz:snapshot")
    AuthorizationSnapshot snapshot(@PathVariable UUID userId,
                                   @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return snapshotService.snapshot(apiKey.projectId(), userId);
    }
}
