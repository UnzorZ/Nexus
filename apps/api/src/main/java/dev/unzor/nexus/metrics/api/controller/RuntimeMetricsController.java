package dev.unzor.nexus.metrics.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.metrics.api.dto.MetricPoint;
import dev.unzor.nexus.metrics.api.requests.RecordMetricRequest;
import dev.unzor.nexus.metrics.application.service.ProjectMetricsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reporte de métricas desde el API de proyecto ({@code /api/v1/metrics}). El
 * {@code projectId} se toma de la API key resuelta, nunca del cuerpo. Scope
 * {@code metrics:write}.
 */
@RestController
@RequestMapping("/api/v1/metrics")
class RuntimeMetricsController {

    private final ProjectMetricsService metricsService;

    RuntimeMetricsController(ProjectMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @PostMapping("/record")
    @RequiredScope("metrics:write")
    MetricPoint record(@Valid @RequestBody RecordMetricRequest request,
                       @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return metricsService.record(apiKey.projectId(), request.name(), request.value(), request.tags());
    }
}
