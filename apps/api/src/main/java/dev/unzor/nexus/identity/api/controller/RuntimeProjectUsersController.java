package dev.unzor.nexus.identity.api.controller;

import java.util.Map;
import java.util.UUID;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.identity.api.dto.ProjectUserDetails;
import dev.unzor.nexus.identity.api.dto.RuntimeProjectUserDetails;
import dev.unzor.nexus.identity.application.service.ProjectUserPasswordResetService;
import dev.unzor.nexus.identity.application.service.ProjectUserQueryService;
import dev.unzor.nexus.identity.application.service.ProjectUserStatusService;
import dev.unzor.nexus.identity.application.service.UpdateProjectUserService;
import dev.unzor.nexus.identity.domain.exception.ProjectUserNotFoundException;
import dev.unzor.nexus.shared.audit.AuditActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de proyecto (máquina, {@code /api/v1/**}, API-key) para gestionar los
 * usuarios finales del propio proyecto desde su backend: leer, editar el perfil,
 * suspender/reactivar, cerrar sesiones y enviar el email de reseteo.
 *
 * <p>El {@code projectId} sale del API key (nunca de la URL), así que una key
 * solo alcanza a los usuarios de su proyecto. Scopes: {@code users:read} para
 * leer y {@code users:write} para mutar. Las mutaciones se auditan con actor
 * {@code API_KEY}. Reutiliza los servicios del panel.</p>
 */
@RestController
@RequestMapping("/api/v1/users")
class RuntimeProjectUsersController {

    private final ProjectUserQueryService queryService;
    private final UpdateProjectUserService updateService;
    private final ProjectUserStatusService statusService;
    private final ProjectUserPasswordResetService passwordResetService;

    RuntimeProjectUsersController(
            ProjectUserQueryService queryService,
            UpdateProjectUserService updateService,
            ProjectUserStatusService statusService,
            ProjectUserPasswordResetService passwordResetService
    ) {
        this.queryService = queryService;
        this.updateService = updateService;
        this.statusService = statusService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * Busca por login OIDC ({@code sub}): username o, si no tiene, email. Incluye
     * {@code roles} (claves en este proyecto) para que el backend llamante pueda
     * revalidar un rol sin esperar al refill de sesión/token.
     */
    @GetMapping("/lookup")
    @RequiredScope("users:read")
    RuntimeProjectUserDetails lookup(@RequestParam String login, @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return queryService.findWithRolesByLogin(apiKey.projectId(), login)
                .orElseThrow(() -> new ProjectUserNotFoundException(apiKey.projectId(), null));
    }

    @GetMapping("/{userId}")
    @RequiredScope("users:read")
    RuntimeProjectUserDetails get(@PathVariable UUID userId, @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return queryService.getWithRoles(apiKey.projectId(), userId);
    }

    /** Cambiar el username cambia el login OIDC: Nexus revoca sesiones y tokens. */
    @PatchMapping("/{userId}")
    @RequiredScope("users:write")
    ProjectUserDetails update(@PathVariable UUID userId, @Valid @RequestBody ProfileUpdate request,
                              @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return updateService.update(apiKey.projectId(), userId, request.displayName(), request.username(),
                AuditActor.apiKey(apiKey.keyId()));
    }

    @PostMapping("/{userId}/suspend")
    @RequiredScope("users:write")
    ProjectUserDetails suspend(@PathVariable UUID userId, @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return statusService.suspend(apiKey.projectId(), userId, AuditActor.apiKey(apiKey.keyId()));
    }

    @PostMapping("/{userId}/reactivate")
    @RequiredScope("users:write")
    ProjectUserDetails reactivate(@PathVariable UUID userId, @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return statusService.reactivate(apiKey.projectId(), userId, AuditActor.apiKey(apiKey.keyId()));
    }

    /** Cierra todas las sesiones y tokens sin cambiar el estado. */
    @PostMapping("/{userId}/revoke-tokens")
    @RequiredScope("users:write")
    ProjectUserDetails revokeTokens(@PathVariable UUID userId, @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return statusService.revokeTokens(apiKey.projectId(), userId, AuditActor.apiKey(apiKey.keyId()));
    }

    /** Envía el email de reseteo. {@code sent=false} si el email no está verificado. */
    @PostMapping("/{userId}/password-reset")
    @RequiredScope("users:write")
    Map<String, Object> passwordReset(@PathVariable UUID userId, @AuthenticationPrincipal ResolvedApiKey apiKey) {
        queryService.get(apiKey.projectId(), userId); // 404 if it is not in this project
        return Map.of("sent", passwordResetService.requestResetFor(apiKey.projectId(), userId));
    }

    record ProfileUpdate(@NotBlank @Size(max = 120) String displayName, @Size(max = 120) String username) {
    }
}
