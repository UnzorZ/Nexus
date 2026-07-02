package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.api.dto.GlobalVariables;
import dev.unzor.nexus.notify.api.dto.NotificationSummary;
import dev.unzor.nexus.notify.api.dto.NotificationTemplateSummary;
import dev.unzor.nexus.notify.api.dto.RenderedTemplate;
import dev.unzor.nexus.notify.domain.entity.Notification;
import dev.unzor.nexus.notify.domain.entity.NotificationTemplate;
import dev.unzor.nexus.notify.domain.entity.ProjectNotifyVariables;
import dev.unzor.nexus.notify.domain.enums.NotificationChannel;
import dev.unzor.nexus.notify.domain.exception.InvalidNotificationRequestException;
import dev.unzor.nexus.notify.domain.exception.NotifyTemplateAlreadyExistsException;
import dev.unzor.nexus.notify.domain.exception.NotifyTemplateNotFoundException;
import dev.unzor.nexus.notify.persistence.repository.NotificationRepository;
import dev.unzor.nexus.notify.persistence.repository.NotificationTemplateRepository;
import dev.unzor.nexus.notify.persistence.repository.ProjectNotifyVariablesRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Casos de uso de notificaciones: CRUD de plantillas, historial y envío. El
 * envío resuelve el mensaje (plantilla o inline), registra un intento, lo envía
 * por SMTP y marca SENT/FAILED; audita ambos resultados.
 */
@Service
public class ProjectNotificationsService {

    // Primary trigger is {$var}; {{var}} is kept as a backwards-compatible fallback.
    private static final Pattern VARIABLE_DOLLAR = Pattern.compile("\\{\\$\\s*(\\w+)\\s*\\}");
    private static final Pattern VARIABLE_MUSTACHE = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");
    private static final Set<String> NAME_UNIQUE_CONSTRAINTS = Set.of("uk_notification_templates_project_name");
    private static final int ERROR_MAX = 500;

    private final NotificationTemplateRepository templateRepository;
    private final NotificationRepository notificationRepository;
    private final ProjectNotifyVariablesRepository globalVariablesRepository;
    private final ProjectLookupService projectLookupService;
    private final NotifyEmailSender emailSender;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectNotificationsService(
            NotificationTemplateRepository templateRepository,
            NotificationRepository notificationRepository,
            ProjectNotifyVariablesRepository globalVariablesRepository,
            ProjectLookupService projectLookupService,
            NotifyEmailSender emailSender,
            ApplicationEventPublisher eventPublisher
    ) {
        this.templateRepository = templateRepository;
        this.notificationRepository = notificationRepository;
        this.globalVariablesRepository = globalVariablesRepository;
        this.projectLookupService = projectLookupService;
        this.emailSender = emailSender;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<NotificationTemplateSummary> listTemplates(UUID projectId) {
        projectLookupService.requireById(projectId);
        return templateRepository.findAllByProjectId(projectId).stream()
                .map(NotificationTemplateSummary::from).toList();
    }

    @Transactional
    public NotificationTemplateSummary createTemplate(UUID projectId, String name, String subject,
                                                      String bodyTemplate, Map<String, String> variables,
                                                      UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        if (templateRepository.existsByProjectIdAndName(projectId, name)) {
            throw new NotifyTemplateAlreadyExistsException(
                    "A template named '" + name + "' already exists in this project.");
        }
        try {
            NotificationTemplate template = new NotificationTemplate(
                    projectId, name, NotificationChannel.EMAIL, subject, bodyTemplate, variables);
            template.assignSequence(nextSequence(projectId));
            NotificationTemplate saved = templateRepository.saveAndFlush(template);
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "notify.template.created", "notification_template",
                    Objects.toString(saved.getId(), null), actorAccountId, Map.of("name", name)));
            return NotificationTemplateSummary.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isNameUniqueViolation(exception)) {
                throw new NotifyTemplateAlreadyExistsException(
                        "A template named '" + name + "' already exists in this project.");
            }
            throw exception;
        }
    }

    /** Siguiente número de secuencia por proyecto (max+1). */
    private int nextSequence(UUID projectId) {
        return templateRepository.findMaxSequenceByProjectId(projectId).orElse(0) + 1;
    }

    @Transactional
    public NotificationTemplateSummary updateTemplate(UUID projectId, UUID templateId, String name,
                                                      String subject, String bodyTemplate, Map<String, String> variables,
                                                      UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        NotificationTemplate template = requireTemplate(projectId, templateId);
        // Rechaza renombrar a un nombre ya usado por OTRA plantilla del proyecto
        // (mantener el nombre actual sí está permitido). Doble validación: pre-check
        // + flush, para que una violación de constraint por concurrencia se traduzca
        // a 409 en vez de un 500 genérico al hacer commit (mismo patrón que create).
        templateRepository.findByProjectIdAndName(projectId, name)
                .filter(existing -> !templateId.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw new NotifyTemplateAlreadyExistsException(
                            "A template named '" + name + "' already exists in this project.");
                });
        template.rewrite(name, subject, bodyTemplate, variables);
        try {
            NotificationTemplate saved = templateRepository.saveAndFlush(template);
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "notify.template.updated", "notification_template", templateId.toString(),
                    actorAccountId, Map.of("name", name)));
            return NotificationTemplateSummary.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isNameUniqueViolation(exception)) {
                throw new NotifyTemplateAlreadyExistsException(
                        "A template named '" + name + "' already exists in this project.");
            }
            throw exception;
        }
    }

    @Transactional
    public void deleteTemplate(UUID projectId, UUID templateId, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        NotificationTemplate template = requireTemplate(projectId, templateId);
        templateRepository.delete(template);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "notify.template.deleted", "notification_template", templateId.toString(),
                actorAccountId, Map.of("name", template.getName())));
    }

    @Transactional(readOnly = true)
    public List<NotificationSummary> listNotifications(UUID projectId) {
        projectLookupService.requireById(projectId);
        return notificationRepository.findTop50ByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(NotificationSummary::from).toList();
    }

    @Transactional
    public NotificationSummary send(UUID projectId, String to, String templateName, String subject,
                                    String body, Map<String, String> variables) {
        projectLookupService.requireById(projectId);
        UUID templateId = null;
        String finalSubject;
        String finalBody;
        if (templateName != null && !templateName.isBlank()) {
            NotificationTemplate template = templateRepository.findByProjectIdAndName(projectId, templateName)
                    .orElseThrow(() -> new NotifyTemplateNotFoundException(
                            "Template '" + templateName + "' not found in project " + projectId + "."));
            templateId = template.getId();
            Map<String, String> resolved = resolveVariables(projectId, template.getVariables(), variables);
            finalSubject = render(template.getSubject(), resolved);
            finalBody = render(template.getBodyTemplate(), resolved);
        } else {
            if (isBlank(subject) || isBlank(body)) {
                throw new InvalidNotificationRequestException(
                        "Either templateName or both subject and body are required.");
            }
            finalSubject = subject;
            finalBody = body;
        }

        Notification notification = notificationRepository.save(
                new Notification(projectId, NotificationChannel.EMAIL, to, templateId, finalSubject, finalBody));
        try {
            emailSender.send(projectId, to, finalSubject, finalBody);
            notification.markSent(Instant.now());
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "notify.sent", "notification", notification.getId().toString(),
                    null, Map.of("recipient", to)));
        } catch (RuntimeException exception) {
            notification.markFailed(truncate(exception.getMessage()));
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "notify.failed", "notification", notification.getId().toString(),
                    null, Map.of("recipient", to)));
        }
        return NotificationSummary.from(notificationRepository.save(notification));
    }

    /**
     * Previsualiza una plantilla (render sin envío) con las variables dadas.
     * Resuelve subject y body sustituyendo los triggers {$var} / {{var}}.
     */
    @Transactional(readOnly = true)
    public RenderedTemplate preview(UUID projectId, UUID templateId, Map<String, String> variables) {
        projectLookupService.requireById(projectId);
        NotificationTemplate template = requireTemplate(projectId, templateId);
        Map<String, String> resolved = resolveVariables(projectId, template.getVariables(), variables);
        // El body se renderiza con marcadores <span class="nx-var" data-var="…"> para
        // que la vista previa resalte dónde cayó cada variable y permita navegar a ella.
        return new RenderedTemplate(render(template.getSubject(), resolved),
                renderHighlighted(template.getBodyTemplate(), resolved));
    }

    /** Lectura de las variables globales del proyecto. */
    @Transactional(readOnly = true)
    public GlobalVariables getGlobalVariables(UUID projectId) {
        projectLookupService.requireById(projectId);
        return globalVariablesRepository.findByProjectId(projectId)
                .map(GlobalVariables::from)
                .orElseGet(() -> new GlobalVariables(projectId, Map.of(), null));
    }

    /** Guardado (reemplazo) de las variables globales del proyecto. */
    @Transactional
    public GlobalVariables saveGlobalVariables(UUID projectId, Map<String, String> variables, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectNotifyVariables entity = globalVariablesRepository.findByProjectId(projectId)
                .orElse(new ProjectNotifyVariables(projectId, variables));
        entity.setVariables(variables);
        ProjectNotifyVariables saved = globalVariablesRepository.save(entity);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "notify.variables.updated", "notify_variables",
                projectId.toString(), actorAccountId,
                Map.of("count", variables == null ? 0 : variables.size())));
        return GlobalVariables.from(saved);
    }

    /**
     * Combina las variables para un render: globales del proyecto &lt; defaults de
     * la plantilla &lt; variables del envío (éstas últimas ganan).
     */
    private Map<String, String> resolveVariables(UUID projectId, Map<String, String> templateDefaults,
                                                 Map<String, String> provided) {
        Map<String, String> merged = new HashMap<>();
        globalVariablesRepository.findByProjectId(projectId)
                .map(ProjectNotifyVariables::getVariables)
                .ifPresent(merged::putAll);
        if (templateDefaults != null) {
            merged.putAll(templateDefaults);
        }
        if (provided != null) {
            merged.putAll(provided);
        }
        return merged;
    }

    private NotificationTemplate requireTemplate(UUID projectId, UUID templateId) {
        return templateRepository.findByProjectIdAndId(projectId, templateId)
                .orElseThrow(() -> new NotifyTemplateNotFoundException(
                        "Template " + templateId + " not found in project " + projectId + "."));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > ERROR_MAX ? value.substring(0, ERROR_MAX) : value;
    }

    /**
     * Sustituye los triggers {@code {$var}} (y el legacy {@code {{var}}}) por su
     * valor; las variables ausentes quedan vacías. Se aplica primero el formato
     * con dólar para que anide correctamente con el de llaves.
     */
    static String render(String text, Map<String, String> variables) {
        String afterDollar = replaceAll(text, VARIABLE_DOLLAR, variables);
        return replaceAll(afterDollar, VARIABLE_MUSTACHE, variables);
    }

    private static String replaceAll(String text, Pattern pattern, Map<String, String> variables) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables == null ? "" : variables.getOrDefault(key, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Igual que {@link #render} pero envuelve cada valor sustituido en
     * {@code <span class="nx-var" data-var="…">valor</span>}. Sólo para la vista
     * previa: así el panel resalta dónde acabó cada variable y puede navegar a la
     * sustitución al hacer clic en la variable. El envío real usa {@link #render}
     * (sin marcadores).
     */
    static String renderHighlighted(String text, Map<String, String> variables) {
        String afterDollar = replaceAllHighlighted(text, VARIABLE_DOLLAR, variables);
        return replaceAllHighlighted(afterDollar, VARIABLE_MUSTACHE, variables);
    }

    private static String replaceAllHighlighted(String text, Pattern pattern, Map<String, String> variables) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(text, last, matcher.start());
            String key = matcher.group(1);
            String value = variables == null ? "" : variables.getOrDefault(key, "");
            // key es \w+ → seguro dentro de un atributo; value se inserta tal cual (HTML).
            result.append("<span class=\"nx-var\" data-var=\"").append(key).append("\">")
                    .append(value)
                    .append("</span>");
            last = matcher.end();
        }
        result.append(text, last, text.length());
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
