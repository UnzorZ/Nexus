package dev.unzor.nexus.documents.api.controller;

import dev.unzor.nexus.documents.api.dto.DocumentRenderSummary;
import dev.unzor.nexus.documents.api.dto.DocumentTemplateSummary;
import dev.unzor.nexus.documents.api.requests.DocumentTemplateRequest;
import dev.unzor.nexus.documents.application.service.ProjectDocumentsService;
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
 * Gestión de plantillas de documento e historial de renders desde el panel. Las
 * escrituras requieren {@code requireManage}; la lectura, {@code requireAccess}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/documents")
class ProjectDocumentsController {

    private final ProjectDocumentsService documentsService;
    private final ProjectAccessService projectAccessService;

    ProjectDocumentsController(ProjectDocumentsService documentsService, ProjectAccessService projectAccessService) {
        this.documentsService = documentsService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping("/templates")
    List<DocumentTemplateSummary> listTemplates(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return documentsService.listTemplates(projectId);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentTemplateSummary createTemplate(
            @PathVariable UUID projectId,
            @Valid @RequestBody DocumentTemplateRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return documentsService.createTemplate(projectId, request.name(), request.contentType(),
                request.templateBody(), principal.accountId());
    }

    @PatchMapping("/templates/{templateId}")
    DocumentTemplateSummary updateTemplate(
            @PathVariable UUID projectId,
            @PathVariable UUID templateId,
            @Valid @RequestBody DocumentTemplateRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return documentsService.updateTemplate(projectId, templateId, request.name(), request.contentType(),
                request.templateBody(), principal.accountId());
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
        documentsService.deleteTemplate(projectId, templateId, principal.accountId());
    }

    @GetMapping("/renders")
    List<DocumentRenderSummary> listRenders(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return documentsService.listRenders(projectId);
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
