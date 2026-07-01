package dev.unzor.nexus.notify.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.notify.api.dto.NotificationSummary;
import dev.unzor.nexus.notify.api.requests.SendNotificationRequest;
import dev.unzor.nexus.notify.application.service.ProjectNotificationsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Envío de notificaciones desde el API de proyecto ({@code /api/v1/notify}). El
 * {@code projectId} se toma de la API key resuelta. Scope {@code notify:send}.
 */
@RestController
@RequestMapping("/api/v1/notify")
class RuntimeNotificationsController {

    private final ProjectNotificationsService notificationsService;

    RuntimeNotificationsController(ProjectNotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @PostMapping("/send")
    @RequiredScope("notify:send")
    NotificationSummary send(@Valid @RequestBody SendNotificationRequest request,
                             @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return notificationsService.send(apiKey.projectId(), request.to(), request.templateName(),
                request.subject(), request.body(), request.variables());
    }
}
