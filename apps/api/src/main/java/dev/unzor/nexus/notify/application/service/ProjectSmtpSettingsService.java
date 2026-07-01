package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.api.dto.SmtpSettingsSummary;
import dev.unzor.nexus.notify.domain.entity.ProjectSmtpSettings;
import dev.unzor.nexus.notify.persistence.repository.ProjectSmtpSettingsRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lectura y guardado de la configuración SMTP de un proyecto. La contraseña se
 * cifra al guardar ({@link NotifyCrypto}) y nunca se devuelve (sólo si está
 * configurada).
 */
@Service
public class ProjectSmtpSettingsService {

    private final ProjectSmtpSettingsRepository repository;
    private final ProjectLookupService projectLookupService;
    private final NotifyCrypto crypto;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectSmtpSettingsService(ProjectSmtpSettingsRepository repository,
                                      ProjectLookupService projectLookupService,
                                      NotifyCrypto crypto,
                                      ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.projectLookupService = projectLookupService;
        this.crypto = crypto;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public SmtpSettingsSummary findForProject(UUID projectId) {
        projectLookupService.requireById(projectId);
        return repository.findByProjectId(projectId)
                .map(SmtpSettingsSummary::from)
                .orElseGet(() -> new SmtpSettingsSummary(projectId, null, 0, null, null, false, null));
    }

    @Transactional
    public SmtpSettingsSummary save(UUID projectId, String host, int port, String username,
                                    String from, String password, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectSmtpSettings settings = repository.findByProjectId(projectId).orElse(null);
        // Si password llega vacía/nula en una actualización, se conserva la existente.
        String passwordEnc = resolvePassword(settings, password);
        if (settings == null) {
            settings = new ProjectSmtpSettings(projectId, host, port, nullToEmpty(username), from, passwordEnc);
        } else {
            settings.rewrite(host, port, nullToEmpty(username), from, passwordEnc);
        }
        ProjectSmtpSettings saved = repository.save(settings);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "notify.smtp.updated", "smtp_settings",
                Objects.toString(saved.getProjectId(), null), actorAccountId,
                Map.of("host", host, "from", from)));
        return SmtpSettingsSummary.from(saved);
    }

    private String resolvePassword(ProjectSmtpSettings existing, String password) {
        if (password != null && !password.isEmpty()) {
            return crypto.encrypt(password);
        }
        return existing == null ? null : existing.getPasswordEnc();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
