package dev.unzor.nexus.config.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.config.api.dto.ConfigValueSummary;
import dev.unzor.nexus.config.application.service.ProjectConfigService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lectura de la configuración de un proyecto desde el API de proyecto
 * ({@code /api/v1/config}). El {@code projectId} se toma de la API key resuelta,
 * nunca del cuerpo. Scope {@code config:read}.
 */
@RestController
@RequestMapping("/api/v1/config")
class RuntimeConfigController {

    private final ProjectConfigService configService;

    RuntimeConfigController(ProjectConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/values")
    @RequiredScope("config:read")
    List<ConfigValueSummary> list(@AuthenticationPrincipal ResolvedApiKey apiKey) {
        return configService.listForProject(apiKey.projectId());
    }

    @GetMapping("/values/{key}")
    @RequiredScope("config:read")
    ConfigValueSummary get(@PathVariable String key, @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return configService.getValue(apiKey.projectId(), key);
    }
}
