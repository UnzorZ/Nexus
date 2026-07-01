package dev.unzor.nexus.notify.api.controller;

import dev.unzor.nexus.notify.api.dto.NotificationSummary;
import dev.unzor.nexus.notify.api.dto.NotificationTemplateSummary;
import dev.unzor.nexus.notify.api.requests.NotificationTemplateRequest;
import dev.unzor.nexus.notify.application.service.ProjectNotificationsService;
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
    private final ProjectAccessService projectAccessService;

    ProjectNotificationsController(ProjectNotificationsService notificationsService,
                                   ProjectAccessService projectAccessService) {
        this.notificationsService = notificationsService;
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
                request.bodyTemplate(), principal.accountId());
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
                request.bodyTemplate(), principal.accountId());
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

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
