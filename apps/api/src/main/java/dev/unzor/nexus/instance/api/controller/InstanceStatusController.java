package dev.unzor.nexus.instance.api.controller;

import dev.unzor.nexus.instance.api.dto.InstanceStatus;
import dev.unzor.nexus.instance.application.service.InstanceStatusService;
import dev.unzor.nexus.shared.security.InstanceAccessRequiredException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Status de sólo lectura de la configuración operativa de la instancia (panel
 * del operador). Requiere {@code ROLE_INSTANCE_ADMIN}. El SMTP (writeable) lo
 * sirve el módulo {@code notify} en {@code /api/panel/v1/instance/smtp}; aquí
 * sólo se expone el status no-SMTP (registro, sesión, vault, keystore JWT,
 * módulos por defecto, heartbeat).
 */
@RestController
@RequestMapping("/api/panel/v1/instance")
public class InstanceStatusController {

    private static final String INSTANCE_ADMIN_AUTHORITY = "ROLE_INSTANCE_ADMIN";

    private final InstanceStatusService statusService;

    public InstanceStatusController(InstanceStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public InstanceStatus getStatus(Authentication authentication) {
        requireInstanceAdmin(authentication);
        return statusService.current();
    }

    private static void requireInstanceAdmin(Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> INSTANCE_ADMIN_AUTHORITY.equals(a.getAuthority()));
        if (!isAdmin) {
            throw new InstanceAccessRequiredException();
        }
    }
}
