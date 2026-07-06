package dev.unzor.nexus.notify.api.controller;

import dev.unzor.nexus.notify.api.dto.GlobalVariables;
import dev.unzor.nexus.notify.api.dto.NotificationSummary;
import dev.unzor.nexus.notify.api.dto.NotificationTemplateSummary;
import dev.unzor.nexus.notify.api.dto.RenderedTemplate;
import dev.unzor.nexus.notify.api.dto.SmtpConnectionCheck;
import dev.unzor.nexus.notify.api.dto.SmtpSettingsSummary;
import dev.unzor.nexus.notify.api.requests.NotificationTemplateRequest;
import dev.unzor.nexus.notify.api.requests.PreviewTemplateRequest;
import dev.unzor.nexus.notify.api.requests.SaveGlobalVariablesRequest;
import dev.unzor.nexus.notify.api.requests.SaveSmtpSettingsRequest;
import dev.unzor.nexus.notify.api.requests.SendTestNotificationRequest;
import dev.unzor.nexus.notify.application.service.NotifyEmailSender;
import dev.unzor.nexus.notify.application.service.ProjectNotificationsService;
import dev.unzor.nexus.notify.application.service.ProjectSmtpSettingsService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Gestión de plantillas de notificación e historial de envíos desde el panel.
 * Escrituras con {@code requireManage}; lectura con {@code requireAccess}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/notify")
class ProjectNotificationsController {

    private final ProjectNotificationsService notificationsService;
    private final ProjectSmtpSettingsService smtpSettingsService;
    private final NotifyEmailSender emailSender;
    private final ProjectAccessService projectAccessService;

    ProjectNotificationsController(ProjectNotificationsService notificationsService,
                                   ProjectSmtpSettingsService smtpSettingsService,
                                   NotifyEmailSender emailSender,
                                   ProjectAccessService projectAccessService) {
        this.notificationsService = notificationsService;
        this.smtpSettingsService = smtpSettingsService;
        this.emailSender = emailSender;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping("/templates")
    List<NotificationTemplateSummary> listTemplates(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return notificationsService.listTemplates(projectId);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    NotificationTemplateSummary createTemplate(
            @PathVariable UUID projectId,
            @Valid @RequestBody NotificationTemplateRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return notificationsService.createTemplate(projectId, request.name(), request.subject(),
                request.bodyTemplate(), request.variables(), principal.accountId());
    }

    @PatchMapping("/templates/{templateId}")
    NotificationTemplateSummary updateTemplate(
            @PathVariable UUID projectId,
            @PathVariable UUID templateId,
            @Valid @RequestBody NotificationTemplateRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return notificationsService.updateTemplate(projectId, templateId, request.name(), request.subject(),
                request.bodyTemplate(), request.variables(), principal.accountId());
    }

    @DeleteMapping("/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTemplate(
            @PathVariable UUID projectId,
            @PathVariable UUID templateId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        notificationsService.deleteTemplate(projectId, templateId, principal.accountId());
    }

    @GetMapping("/notifications")
    List<NotificationSummary> listNotifications(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return notificationsService.listNotifications(projectId);
    }

    @GetMapping("/smtp")
    SmtpSettingsSummary getSmtpSettings(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return smtpSettingsService.findForProject(projectId);
    }

    @PutMapping("/smtp")
    SmtpSettingsSummary saveSmtpSettings(
            @PathVariable UUID projectId,
            @Valid @RequestBody SaveSmtpSettingsRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return smtpSettingsService.save(projectId, request.host(), request.port(), request.username(),
                request.from(), request.password(), request.tlsMode(), request.trustedCaPem(),
                principal.accountId());
    }

    /**
     * Comprueba la conexión SMTP guardada (resolve + anti-SSRF + STARTTLS verificado
     * + AUTH) sin enviar correo. requireManage: expone el resultado de la conexión.
     */
    @PostMapping("/smtp/test-connection")
    SmtpConnectionCheck testSmtpConnection(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return emailSender.testConnection(projectId);
    }

    @PostMapping("/templates/{templateId}/preview")
    RenderedTemplate previewTemplate(
            @PathVariable UUID projectId,
            @PathVariable UUID templateId,
            @Valid @RequestBody PreviewTemplateRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return notificationsService.preview(projectId, templateId, request.variables());
    }

    @GetMapping("/variables")
    GlobalVariables getGlobalVariables(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return notificationsService.getGlobalVariables(projectId);
    }

    @PutMapping("/variables")
    GlobalVariables saveGlobalVariables(
            @PathVariable UUID projectId,
            @Valid @RequestBody SaveGlobalVariablesRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return notificationsService.saveGlobalVariables(projectId, request.variables(), principal.accountId());
    }

    /** Envía un email de prueba a una dirección indicada por el usuario. */
    @PostMapping("/test")
    NotificationSummary sendTest(
            @PathVariable UUID projectId,
            @Valid @RequestBody SendTestNotificationRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return notificationsService.send(projectId, request.to(), request.templateName(),
                request.subject(), request.body(), request.variables(), principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
