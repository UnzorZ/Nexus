package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.api.dto.SmtpSettingsSummary;
import dev.unzor.nexus.notify.domain.entity.ProjectSmtpSettings;
import dev.unzor.nexus.notify.domain.enums.SmtpTlsMode;
import dev.unzor.nexus.notify.domain.exception.InvalidNotificationRequestException;
import dev.unzor.nexus.notify.persistence.repository.ProjectSmtpSettingsRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
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
                .orElseGet(() -> new SmtpSettingsSummary(projectId, null, 0, null, null, false,
                        SmtpTlsMode.PUBLIC.name(), false, null));
    }

    @Transactional
    public SmtpSettingsSummary save(UUID projectId, String host, int port, String username,
                                    String from, String password, String tlsMode, String trustedCaPem,
                                    UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        SmtpTlsMode mode = resolveTlsMode(tlsMode);
        // Fail-fast: rechaza hosts internos/loopback/metadata antes de guardar (anti-SSRF).
        SmtpHostGuard.assertSafe(host);

        ProjectSmtpSettings settings = repository.findByProjectId(projectId).orElse(null);
        // Si password llega vacía/nula en una actualización, se conserva la existente.
        String passwordEnc = resolvePassword(settings, password);
        // Igual con la CA pinneada: vacía conserva la existente (igual que la password).
        String pinnedCa = resolvePinnedCa(mode, trustedCaPem, settings);
        String modeName = mode.name();
        if (settings == null) {
            settings = new ProjectSmtpSettings(projectId, host, port, nullToEmpty(username), from,
                    passwordEnc, modeName, pinnedCa);
        } else {
            settings.rewrite(host, port, nullToEmpty(username), from, passwordEnc, modeName, pinnedCa);
        }
        ProjectSmtpSettings saved = repository.save(settings);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "notify.smtp.updated", "smtp_settings",
                Objects.toString(saved.getProjectId(), null), actorAccountId,
                Map.of("host", host, "from", from, "tlsMode", modeName)));
        return SmtpSettingsSummary.from(saved);
    }

    private static SmtpTlsMode resolveTlsMode(String tlsMode) {
        try {
            return SmtpTlsMode.resolve(tlsMode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidNotificationRequestException(
                    "tlsMode must be PUBLIC or PINNED (got '" + tlsMode + "').");
        }
    }

    /**
     * Resuelve la CA para el modo PINNED: si llega una nueva la valida; si llega
     * vacía conserva la existente (patrón "dejar en blanco para mantener", igual
     * que la password); si no hay existente, la exige.
     */
    private static String resolvePinnedCa(SmtpTlsMode mode, String trustedCaPem, ProjectSmtpSettings existing) {
        if (mode != SmtpTlsMode.PINNED) {
            return null;
        }
        if (trustedCaPem != null && !trustedCaPem.isBlank()) {
            validatePem(trustedCaPem);
            return trustedCaPem;
        }
        String existingCa = existing == null ? null : existing.getTrustedCaPem();
        if (existingCa == null || existingCa.isBlank()) {
            throw new InvalidNotificationRequestException(
                    "A trusted CA certificate (PEM) is required for self-signed SMTP (tlsMode=PINNED).");
        }
        return existingCa;
    }

    private static void validatePem(String trustedCaPem) {
        try {
            CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(trustedCaPem.getBytes(StandardCharsets.UTF_8)));
        } catch (CertificateException exception) {
            throw new InvalidNotificationRequestException(
                    "The trusted CA certificate is not a valid PEM X.509 certificate.");
        }
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
