package dev.unzor.nexus.registry.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.registry.api.dto.HeartbeatReceipt;
import dev.unzor.nexus.registry.api.requests.HeartbeatRequest;
import dev.unzor.nexus.registry.application.service.RegistryHeartbeatService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Endpoint de latido del API de proyecto (spec §13.1). Las apps lo llaman con
 * {@code X-Nexus-Api-Key} (scope {@code registry:heartbeat}); el
 * {@code projectId} se toma del principal resuelto por la API key, nunca del
 * cuerpo (spec §10.2). La cadena de seguridad {@code /api/v1/**} y el
 * {@code RequiredScopeInterceptor} ya se encargan de auth + scope.
 */
@RestController
@RequestMapping("/api/v1/registry")
class RegistryHeartbeatController {

    private final RegistryHeartbeatService service;

    RegistryHeartbeatController(RegistryHeartbeatService service) {
        this.service = service;
    }

    @PostMapping("/heartbeat")
    @RequiredScope("registry:heartbeat")
    HeartbeatReceipt heartbeat(@Valid @RequestBody HeartbeatRequest request,
                               @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return service.record(apiKey.projectId(), apiKey.keyId(), request, Instant.now());
    }
}
