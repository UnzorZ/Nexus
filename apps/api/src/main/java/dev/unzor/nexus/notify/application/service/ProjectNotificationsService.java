package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.api.dto.NotificationSummary;
import dev.unzor.nexus.notify.api.dto.NotificationTemplateSummary;
import dev.unzor.nexus.notify.domain.entity.Notification;
import dev.unzor.nexus.notify.domain.entity.NotificationTemplate;
import dev.unzor.nexus.notify.domain.enums.NotificationChannel;
import dev.unzor.nexus.notify.domain.exception.InvalidNotificationRequestException;
import dev.unzor.nexus.notify.domain.exception.NotifyTemplateAlreadyExistsException;
import dev.unzor.nexus.notify.domain.exception.NotifyTemplateNotFoundException;
import dev.unzor.nexus.notify.persistence.repository.NotificationRepository;
import dev.unzor.nexus.notify.persistence.repository.NotificationTemplateRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");
    private static final Set<String> NAME_UNIQUE_CONSTRAINTS = Set.of("uk_notification_templates_project_name");
    private static final int ERROR_MAX = 500;

    private final NotificationTemplateRepository templateRepository;
    private final NotificationRepository notificationRepository;
    private final ProjectLookupService projectLookupService;
    private final NotifyEmailSender emailSender;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectNotificationsService(
            NotificationTemplateRepository templateRepository,
            NotificationRepository notificationRepository,
            ProjectLookupService projectLookupService,
            NotifyEmailSender emailSender,
            ApplicationEventPublisher eventPublisher
    ) {
        this.templateRepository = templateRepository;
        this.notificationRepository = notificationRepository;
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
                                                      String bodyTemplate, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        if (templateRepository.existsByProjectIdAndName(projectId, name)) {
            throw new NotifyTemplateAlreadyExistsException(
                    "A template named '" + name + "' already exists in this project.");
        }
        try {
            NotificationTemplate saved = templateRepository.saveAndFlush(
                    new NotificationTemplate(projectId, name, NotificationChannel.EMAIL, subject, bodyTemplate));
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

    @Transactional
    public NotificationTemplateSummary updateTemplate(UUID projectId, UUID templateId, String name,
                                                      String subject, String bodyTemplate, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        NotificationTemplate template = requireTemplate(projectId, templateId);
        template.rewrite(name, subject, bodyTemplate);
        NotificationTemplateSummary summary = NotificationTemplateSummary.from(templateRepository.save(template));
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "notify.template.updated", "notification_template", templateId.toString(),
                actorAccountId, Map.of("name", name)));
        return summary;
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
            finalSubject = render(template.getSubject(), variables);
            finalBody = render(template.getBodyTemplate(), variables);
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
            emailSender.send(to, finalSubject, finalBody);
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

    static String render(String text, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(text);
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
