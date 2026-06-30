package dev.unzor.nexus.registry.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.security.InstanceTokenService;
import dev.unzor.nexus.apikeys.security.RawApiKeyRequiredException;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.apikeys.security.ResolvedCredential;
import dev.unzor.nexus.registry.api.dto.HeartbeatReceipt;
import dev.unzor.nexus.registry.api.requests.HeartbeatRequest;
import dev.unzor.nexus.registry.application.service.RegistryHeartbeatService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoint de latido del API de proyecto (spec §13.1). Las apps lo llaman con
 * {@code X-Nexus-Api-Key} o con un instance token (ADR-0012); el
 * {@code projectId} se toma del principal resuelto, nunca del cuerpo (spec
 * §10.2). La cadena de seguridad {@code /api/v1/**} y el
 * {@code RequiredScopeInterceptor} ya se encargan de auth + scope.
 */
@RestController
@RequestMapping("/api/v1/registry")
class RegistryHeartbeatController {

    private final RegistryHeartbeatService service;
    private final InstanceTokenService instanceTokenService;

    RegistryHeartbeatController(RegistryHeartbeatService service, InstanceTokenService instanceTokenService) {
        this.service = service;
        this.instanceTokenService = instanceTokenService;
    }

    /**
     * Handshake del SDK (ADR-0012): presenta la API key larga con scope
     * {@code registry:heartbeat} y recibe un instance token efímero (TTL por
     * defecto 1h) que puede usar en latidos posteriores en vez de la key cruda.
     * <p>
     * Debe bootstraparse con la key cruda, no con un token previo: si no, un
     * cliente con un token podría renovarlo indefinidamente sin volver a
     * presentar la key, eludiendo su rotación/revocación. Se rechaza con
     * {@link RawApiKeyRequiredException} (401 {@code raw_api_key_required}) si la
     * request llegó autenticada por instance token.
     */
    @PostMapping("/register")
    @RequiredScope("registry:heartbeat")
    Map<String, Object> register(@AuthenticationPrincipal ResolvedApiKey apiKey,
                                 Authentication authentication) {
        if (!ResolvedCredential.API_KEY.equals(authentication.getDetails())) {
            throw new RawApiKeyRequiredException();
        }
        InstanceTokenService.Issued issued = instanceTokenService.mint(apiKey);
        return Map.of(
                "token", issued.token(),
                "tokenType", "bearer",
                "expiresInSeconds", issued.expiresInSeconds(),
                "projectId", apiKey.projectId().toString()
        );
    }

    @PostMapping("/heartbeat")
    @RequiredScope("registry:heartbeat")
    HeartbeatReceipt heartbeat(@Valid @RequestBody HeartbeatRequest request,
                               @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return service.record(apiKey.projectId(), apiKey.keyId(), apiKey.keyPrefix(), request, Instant.now());
    }
}
