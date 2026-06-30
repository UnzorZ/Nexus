package dev.unzor.nexus.audit.api.controller;

import dev.unzor.nexus.audit.api.dto.AuditPage;
import dev.unzor.nexus.audit.application.service.AuditQueryService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Listado del log de auditoría de un proyecto para el panel (ADR-0004). Solo
 * lectura. Requiere {@code requireAccess}: cualquier miembro del proyecto (o un
 * instance admin) puede verlo. Acota por {@code since} y PAGINA con
 * {@code page}/{@code size} (50 por página, máx. 100); el filtrado fino va en el
 * cliente sobre la página cargada.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/audit")
class ProjectAuditController {

    private final AuditQueryService service;
    private final ProjectAccessService projectAccessService;

    ProjectAuditController(AuditQueryService service, ProjectAccessService projectAccessService) {
        this.service = service;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    AuditPage list(
            @PathVariable UUID projectId,
            @RequestParam(name = "since", required = false) Instant since,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin(authentication));
        return AuditPage.from(service.listForProject(projectId, since, page, size));
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
