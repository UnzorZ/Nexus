package dev.unzor.nexus.instance.api.controller;

import dev.unzor.nexus.instance.api.dto.InstanceSettingsView;
import dev.unzor.nexus.instance.api.requests.InstanceSettingsRequests;
import dev.unzor.nexus.instance.application.service.InstanceSettingsService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import dev.unzor.nexus.shared.security.InstanceAccessRequiredException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Configuración writeable de instancia (panel del operador). Requiere
 * {@code ROLE_INSTANCE_ADMIN}. SMTP va aparte (módulo notify,
 * {@code /instance/smtp}); el status read-only en {@code GET /instance/status}.
 */
@RestController
@RequestMapping("/api/panel/v1/instance")
public class InstanceSettingsController {

    private static final String INSTANCE_ADMIN_AUTHORITY = "ROLE_INSTANCE_ADMIN";

    private final InstanceSettingsService settingsService;

    public InstanceSettingsController(InstanceSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public InstanceSettingsView getSettings(Authentication authentication) {
        requireInstanceAdmin(authentication);
        return settingsService.view();
    }

    @PutMapping("/registration")
    public InstanceSettingsView setRegistration(
            @Valid @RequestBody InstanceSettingsRequests.SaveRegistrationRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication) {
        requireInstanceAdmin(authentication);
        return settingsService.setRegistrationOpen(request.open(), principal.accountId());
    }

    @PutMapping("/modules-defaults")
    public InstanceSettingsView setDefaultModules(
            @RequestBody InstanceSettingsRequests.SaveDefaultModulesRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication) {
        requireInstanceAdmin(authentication);
        List<String> modules = request == null ? null : request.modules();
        return settingsService.setDefaultModules(modules, principal.accountId());
    }

    @PutMapping("/heartbeat")
    public InstanceSettingsView setHeartbeat(
            @RequestBody InstanceSettingsRequests.SaveHeartbeatDefaultsRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication) {
        requireInstanceAdmin(authentication);
        InstanceSettingsRequests.SaveHeartbeatDefaultsRequest body =
                request == null
                        ? new InstanceSettingsRequests.SaveHeartbeatDefaultsRequest(null, null)
                        : request;
        return settingsService.setHeartbeat(body.intervalSeconds(), body.timeoutSeconds(), principal.accountId());
    }

    private static void requireInstanceAdmin(Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> INSTANCE_ADMIN_AUTHORITY.equals(a.getAuthority()));
        if (!isAdmin) {
            throw new InstanceAccessRequiredException();
        }
    }
}
