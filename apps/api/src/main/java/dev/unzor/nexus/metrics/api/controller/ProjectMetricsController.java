package dev.unzor.nexus.metrics.api.controller;

import dev.unzor.nexus.metrics.api.dto.MetricSeries;
import dev.unzor.nexus.metrics.application.service.ProjectMetricsService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Consulta de métricas de un proyecto desde el panel (series agregadas por
 * nombre). Lectura con {@code requireAccess}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/metrics")
class ProjectMetricsController {

    private final ProjectMetricsService metricsService;
    private final ProjectAccessService projectAccessService;

    ProjectMetricsController(ProjectMetricsService metricsService, ProjectAccessService projectAccessService) {
        this.metricsService = metricsService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<MetricSeries> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return metricsService.seriesForProject(projectId);
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
