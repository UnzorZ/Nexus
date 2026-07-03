package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.api.dto.SmtpSettingsSummary;
import dev.unzor.nexus.notify.domain.entity.InstanceSmtpSettings;
import dev.unzor.nexus.notify.domain.enums.SmtpTlsMode;
import dev.unzor.nexus.notify.domain.exception.InvalidNotificationRequestException;
import dev.unzor.nexus.notify.persistence.repository.InstanceSmtpSettingsRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Map;
import java.util.UUID;

/**
 * Lectura y guardado de la configuración SMTP de la instancia (singleton). Es la
 * fuente por defecto del envío de email de todos los proyectos; si no hay fila,
 * el envío cae a {@code nexus.notify.smtp.*} (env). La contraseña se cifra al
 * guardar ({@link NotifyCrypto}) y nunca se devuelve (sólo si está configurada).
 *
 * <p>Auditoría a nivel instancia ({@code projectId=null}, recurso
 * {@code instance_smtp_settings}).</p>
 */
@Service
public class InstanceSmtpSettingsService {

    private static final Short SINGLETON_ID = 1;

    private final InstanceSmtpSettingsRepository repository;
    private final NotifyCrypto crypto;
    private final ApplicationEventPublisher eventPublisher;

    public InstanceSmtpSettingsService(InstanceSmtpSettingsRepository repository,
                                       NotifyCrypto crypto,
                                       ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.crypto = crypto;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public SmtpSettingsSummary findSummary() {
        return repository.findById(SINGLETON_ID)
                .map(InstanceSmtpSettingsService::toSummary)
                .orElseGet(() -> new SmtpSettingsSummary(null, null, 0, null, null, false,
                        SmtpTlsMode.PUBLIC.name(), false, null));
    }

    @Transactional
    public SmtpSettingsSummary save(String host, int port, String username, String from,
                                    String password, String tlsMode, String trustedCaPem,
                                    UUID actorAccountId) {
        SmtpTlsMode mode = resolveTlsMode(tlsMode);
        // Fail-fast: rechaza hosts internos/loopback/metadata antes de guardar (anti-SSRF).
        SmtpHostGuard.assertSafe(host);

        InstanceSmtpSettings existing = repository.findById(SINGLETON_ID).orElse(null);
        // Si password llega vacía/nula en una actualización, se conserva la existente.
        String passwordEnc = resolvePassword(existing, password);
        // Igual con la CA pinneada: vacía conserva la existente (igual que la password).
        String pinnedCa = resolvePinnedCa(mode, trustedCaPem, existing);
        String modeName = mode.name();
        if (existing == null) {
            existing = new InstanceSmtpSettings(host, port, nullToEmpty(username), from,
                    passwordEnc, modeName, pinnedCa, actorAccountId);
        } else {
            existing.rewrite(host, port, nullToEmpty(username), from, passwordEnc, modeName, pinnedCa, actorAccountId);
        }
        InstanceSmtpSettings saved = repository.save(existing);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                null, "instance.smtp.updated", "instance_smtp_settings", "instance", actorAccountId,
                Map.of("host", host, "from", from, "tlsMode", modeName)));
        return toSummary(saved);
    }

    private static SmtpSettingsSummary toSummary(InstanceSmtpSettings settings) {
        return new SmtpSettingsSummary(
                null,
                settings.getHost(),
                settings.getPort() == null ? 0 : settings.getPort(),
                settings.getUsername(),
                settings.getFromAddress(),
                settings.getPasswordEnc() != null && !settings.getPasswordEnc().isBlank(),
                SmtpTlsMode.resolve(settings.getTlsMode()).name(),
                settings.getTrustedCaPem() != null && !settings.getTrustedCaPem().isBlank(),
                settings.getUpdatedAt());
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
    private static String resolvePinnedCa(SmtpTlsMode mode, String trustedCaPem, InstanceSmtpSettings existing) {
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

    private String resolvePassword(InstanceSmtpSettings existing, String password) {
        if (password != null && !password.isEmpty()) {
            return crypto.encrypt(password);
        }
        return existing == null ? null : existing.getPasswordEnc();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
