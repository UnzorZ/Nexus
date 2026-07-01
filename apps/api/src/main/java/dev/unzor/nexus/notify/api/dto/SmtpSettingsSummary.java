package dev.unzor.nexus.notify.api.dto;

import dev.unzor.nexus.notify.domain.entity.ProjectSmtpSettings;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de la configuración SMTP de un proyecto. La contraseña nunca se
 * devuelve; sólo si está configurada.
 */
public record SmtpSettingsSummary(
        UUID projectId,
        String host,
        int port,
        String username,
        String from,
        boolean passwordConfigured,
        Instant updatedAt
) {
    public static SmtpSettingsSummary from(ProjectSmtpSettings settings) {
        return new SmtpSettingsSummary(
                settings.getProjectId(),
                settings.getHost(),
                settings.getPort(),
                settings.getUsername(),
                settings.getFromAddress(),
                settings.getPasswordEnc() != null && !settings.getPasswordEnc().isBlank(),
                settings.getUpdatedAt());
    }
}
