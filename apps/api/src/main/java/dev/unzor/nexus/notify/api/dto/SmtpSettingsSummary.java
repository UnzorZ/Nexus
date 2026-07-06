package dev.unzor.nexus.notify.api.dto;

import dev.unzor.nexus.notify.domain.entity.ProjectSmtpSettings;
import dev.unzor.nexus.notify.domain.enums.SmtpTlsMode;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de la configuración SMTP de un proyecto. La contraseña y la CA subida
 * nunca se devuelven; sólo si están configuradas.
 */
public record SmtpSettingsSummary(
        UUID projectId,
        String host,
        int port,
        String username,
        String from,
        boolean passwordConfigured,
        String tlsMode,
        boolean trustedCaConfigured,
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
                SmtpTlsMode.resolve(settings.getTlsMode()).name(),
                settings.getTrustedCaPem() != null && !settings.getTrustedCaPem().isBlank(),
                settings.getUpdatedAt());
    }
}
