package dev.unzor.nexus.notify.api.controller;

import dev.unzor.nexus.notify.api.dto.SmtpConnectionCheck;
import dev.unzor.nexus.notify.api.dto.SmtpSettingsSummary;
import dev.unzor.nexus.notify.api.requests.SaveSmtpSettingsRequest;
import dev.unzor.nexus.notify.application.service.InstanceSmtpSettingsService;
import dev.unzor.nexus.notify.application.service.NotifyEmailSender;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import dev.unzor.nexus.shared.security.InstanceAccessRequiredException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SMTP a nivel de instancia (relay del operador, ADR-0014): la fuente por
 * defecto del envío de email de todos los proyectos. Vive en el módulo
 * {@code notify} porque SMTP es su dominio (reusa sus servicios y excepciones);
 * la ruta es {@code /api/panel/v1/instance} (no project-scoped). Todos los
 * endpoints <strong>requieren</strong> {@code ROLE_INSTANCE_ADMIN}.
 *
 * <p>El status de la demás config operativa (no SMTP) lo sirve el módulo
 * {@code instance} en {@code GET /instance/status}.</p>
 */
@RestController
@RequestMapping("/api/panel/v1/instance")
class InstanceSmtpSettingsController {

    private static final String INSTANCE_ADMIN_AUTHORITY = "ROLE_INSTANCE_ADMIN";

    private final InstanceSmtpSettingsService smtpSettingsService;
    private final NotifyEmailSender emailSender;

    InstanceSmtpSettingsController(InstanceSmtpSettingsService smtpSettingsService,
                                   NotifyEmailSender emailSender) {
        this.smtpSettingsService = smtpSettingsService;
        this.emailSender = emailSender;
    }

    @GetMapping("/smtp")
    SmtpSettingsSummary getSmtp(@AuthenticationPrincipal AuthenticatedAccount principal,
                                Authentication authentication) {
        requireInstanceAdmin(authentication);
        return smtpSettingsService.findSummary();
    }

    @PutMapping("/smtp")
    SmtpSettingsSummary saveSmtp(@Valid @RequestBody SaveSmtpSettingsRequest request,
                                 @AuthenticationPrincipal AuthenticatedAccount principal,
                                 Authentication authentication) {
        requireInstanceAdmin(authentication);
        return smtpSettingsService.save(request.host(), request.port(), request.username(),
                request.from(), request.password(), request.tlsMode(), request.trustedCaPem(),
                principal.accountId());
    }

    /** Comprueba la conexión del SMTP de instancia (anti-SSRF + STARTTLS + AUTH), sin enviar correo. */
    @PostMapping("/smtp/test-connection")
    SmtpConnectionCheck testSmtpConnection(Authentication authentication) {
        requireInstanceAdmin(authentication);
        return emailSender.testInstanceConnection();
    }

    private static void requireInstanceAdmin(Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> INSTANCE_ADMIN_AUTHORITY.equals(a.getAuthority()));
        if (!isAdmin) {
            throw new InstanceAccessRequiredException();
        }
    }
}
