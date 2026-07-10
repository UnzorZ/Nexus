package dev.unzor.nexus.permissions.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.permissions.api.dto.PermissionDeclaration;
import dev.unzor.nexus.permissions.api.dto.PermissionDeclarationReceipt;
import dev.unzor.nexus.permissions.application.service.ProjectPermissionsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de proyecto para permisos (máquina, {@code /api/v1/**}, API-key). Expone la
 * <b>sincronización declarativa</b> que el SDK de Nexus (spec §18) usa para que
 * una aplicación declare qué claves de permiso usa: Nexus las registra en el
 * catálogo del proyecto (origen {@code CODE}) y marca las que dejó de declarar.
 *
 * <p>El {@code projectId} se toma del API key resuelto (no del cuerpo); la cadena
 * {@code /api/v1/**} y el {@code RequiredScopeInterceptor} se encargan de auth +
 * scope ({@code permissions:declare}).</p>
 */
@RestController
@RequestMapping("/api/v1/permissions")
class RuntimePermissionsController {

    private final ProjectPermissionsService permissionsService;

    RuntimePermissionsController(ProjectPermissionsService permissionsService) {
        this.permissionsService = permissionsService;
    }

    @PostMapping("/declare")
    @RequiredScope("permissions:declare")
    PermissionDeclarationReceipt declare(@Valid @RequestBody List<PermissionDeclaration> declarations,
                                         @RequestHeader(value = "X-Nexus-App", required = false) String app,
                                         @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return permissionsService.declare(apiKey.projectId(), app, declarations);
    }
}
