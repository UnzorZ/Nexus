package dev.unzor.nexus.documents.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.documents.api.dto.DocumentRenderResult;
import dev.unzor.nexus.documents.api.requests.RenderDocumentRequest;
import dev.unzor.nexus.documents.application.service.ProjectDocumentsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Render de documentos desde el API de proyecto ({@code /api/v1/documents}). El
 * {@code projectId} se toma de la API key resuelta. Scope {@code documents:render}.
 */
@RestController
@RequestMapping("/api/v1/documents")
class RuntimeDocumentsController {

    private final ProjectDocumentsService documentsService;

    RuntimeDocumentsController(ProjectDocumentsService documentsService) {
        this.documentsService = documentsService;
    }

    @PostMapping("/render")
    @RequiredScope("documents:render")
    DocumentRenderResult render(@Valid @RequestBody RenderDocumentRequest request,
                                @AuthenticationPrincipal ResolvedApiKey apiKey) {
        return documentsService.render(apiKey.projectId(), request.templateName(), request.variables());
    }
}
