package dev.unzor.nexus.documents.application.service;

import dev.unzor.nexus.documents.api.dto.DocumentRenderResult;
import dev.unzor.nexus.documents.api.dto.DocumentRenderSummary;
import dev.unzor.nexus.documents.api.dto.DocumentTemplateSummary;
import dev.unzor.nexus.documents.domain.entity.DocumentRender;
import dev.unzor.nexus.documents.domain.entity.DocumentTemplate;
import dev.unzor.nexus.documents.domain.exception.DocumentTemplateAlreadyExistsException;
import dev.unzor.nexus.documents.domain.exception.DocumentTemplateNotFoundException;
import dev.unzor.nexus.documents.persistence.repository.DocumentRenderRepository;
import dev.unzor.nexus.documents.persistence.repository.DocumentTemplateRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Casos de uso de plantillas y renders de documentos. El render hace
 * sustitución simple de {@code {{var}}} (sin motor externo).
 */
@Service
public class ProjectDocumentsService {

    // Primary trigger is {$var}; {{var}} is kept as a backwards-compatible fallback.
    private static final Pattern VARIABLE_DOLLAR = Pattern.compile("\\{\\$\\s*(\\w+)\\s*\\}");
    private static final Pattern VARIABLE_MUSTACHE = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");
    private static final Set<String> NAME_UNIQUE_CONSTRAINTS = Set.of("uk_document_templates_project_name");

    private final DocumentTemplateRepository templateRepository;
    private final DocumentRenderRepository renderRepository;
    private final ProjectLookupService projectLookupService;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectDocumentsService(
            DocumentTemplateRepository templateRepository,
            DocumentRenderRepository renderRepository,
            ProjectLookupService projectLookupService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.templateRepository = templateRepository;
        this.renderRepository = renderRepository;
        this.projectLookupService = projectLookupService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplateSummary> listTemplates(UUID projectId) {
        projectLookupService.requireById(projectId);
        return templateRepository.findAllByProjectId(projectId).stream()
                .map(DocumentTemplateSummary::from).toList();
    }

    @Transactional
    public DocumentTemplateSummary createTemplate(UUID projectId, String name, String contentType,
                                                  String templateBody, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        if (templateRepository.existsByProjectIdAndName(projectId, name)) {
            throw new DocumentTemplateAlreadyExistsException(
                    "A template named '" + name + "' already exists in this project.");
        }
        try {
            DocumentTemplate saved = templateRepository.saveAndFlush(
                    new DocumentTemplate(projectId, name, contentType, templateBody));
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "documents.template.created", "document_template",
                    Objects.toString(saved.getId(), null), actorAccountId, Map.of("name", name)));
            return DocumentTemplateSummary.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isNameUniqueViolation(exception)) {
                throw new DocumentTemplateAlreadyExistsException(
                        "A template named '" + name + "' already exists in this project.");
            }
            throw exception;
        }
    }

    @Transactional
    public DocumentTemplateSummary updateTemplate(UUID projectId, UUID templateId, String name,
                                                  String contentType, String templateBody, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        DocumentTemplate template = requireTemplate(projectId, templateId);
        // Rechaza renombrar a un nombre ya usado por OTRO template del proyecto
        // (mantener el nombre actual sí está permitido). Doble validación: pre-check
        // + flush, para que una violación de constraint por concurrencia se traduzca
        // a 409 en vez de un 500 genérico al hacer commit (mismo patrón que create).
        templateRepository.findByProjectIdAndName(projectId, name)
                .filter(existing -> !templateId.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw new DocumentTemplateAlreadyExistsException(
                            "A template named '" + name + "' already exists in this project.");
                });
        template.rewrite(name, contentType, templateBody);
        try {
            DocumentTemplate saved = templateRepository.saveAndFlush(template);
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "documents.template.updated", "document_template", templateId.toString(),
                    actorAccountId, Map.of("name", name)));
            return DocumentTemplateSummary.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isNameUniqueViolation(exception)) {
                throw new DocumentTemplateAlreadyExistsException(
                        "A template named '" + name + "' already exists in this project.");
            }
            throw exception;
        }
    }

    @Transactional
    public void deleteTemplate(UUID projectId, UUID templateId, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        DocumentTemplate template = requireTemplate(projectId, templateId);
        templateRepository.delete(template);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "documents.template.deleted", "document_template", templateId.toString(),
                actorAccountId, Map.of("name", template.getName())));
    }

    @Transactional(readOnly = true)
    public List<DocumentRenderSummary> listRenders(UUID projectId) {
        projectLookupService.requireById(projectId);
        return renderRepository.findTop50ByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(DocumentRenderSummary::from).toList();
    }

    @Transactional
    public DocumentRenderResult render(UUID projectId, String templateName, Map<String, String> variables) {
        projectLookupService.requireById(projectId);
        DocumentTemplate template = templateRepository.findByProjectIdAndName(projectId, templateName)
                .orElseThrow(() -> new DocumentTemplateNotFoundException(
                        "Template '" + templateName + "' not found in project " + projectId + "."));
        String output = renderBody(template.getTemplateBody(), variables);
        DocumentRender saved = renderRepository.save(
                new DocumentRender(projectId, template.getId(), template.getName(), variables, output));
        // No se audita en audit_log: el propio document_renders es el historial
        // (y el actor es una API key, no una cuenta). Los CRUD de plantilla sí se
        // auditan con el actor real.
        return new DocumentRenderResult(saved.getId(), output, template.getContentType(), saved.getCreatedAt());
    }

    /**
     * Render del panel (por ID de plantilla). A diferencia del render del
     * runtime, éste sí audita porque el actor es una cuenta del panel.
     */
    @Transactional
    public DocumentRenderResult renderById(UUID projectId, UUID templateId,
                                           Map<String, String> variables, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        DocumentTemplate template = requireTemplate(projectId, templateId);
        String output = renderBody(template.getTemplateBody(), variables);
        DocumentRender saved = renderRepository.save(
                new DocumentRender(projectId, template.getId(), template.getName(), variables, output));
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "documents.rendered", "document_template", templateId.toString(),
                actorAccountId, Map.of("name", template.getName())));
        return new DocumentRenderResult(saved.getId(), output, template.getContentType(), saved.getCreatedAt());
    }

    private DocumentTemplate requireTemplate(UUID projectId, UUID templateId) {
        return templateRepository.findByProjectIdAndId(projectId, templateId)
                .orElseThrow(() -> new DocumentTemplateNotFoundException(
                        "Template " + templateId + " not found in project " + projectId + "."));
    }

    /**
     * Sustituye los triggers {@code {$var}} (y el legacy {@code {{var}}}) por su
     * valor; las variables ausentes quedan vacías. Se aplica primero el formato
     * con dólar para que anide correctamente con el de llaves.
     */
    static String renderBody(String body, Map<String, String> variables) {
        String afterDollar = replaceAll(body, VARIABLE_DOLLAR, variables);
        return replaceAll(afterDollar, VARIABLE_MUSTACHE, variables);
    }

    private static String replaceAll(String body, Pattern pattern, Map<String, String> variables) {
        Matcher matcher = pattern.matcher(body);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables == null ? "" : variables.getOrDefault(key, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean isNameUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && NAME_UNIQUE_CONSTRAINTS.contains(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
