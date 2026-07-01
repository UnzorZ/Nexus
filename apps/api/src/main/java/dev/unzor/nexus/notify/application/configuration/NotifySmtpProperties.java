package dev.unzor.nexus.notify.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración SMTP del canal email ({@code nexus.notify.smtp.*}). Si
 * {@code host} (o {@code from}) está en blanco, el envío falla con
 * {@code smtp_not_configured} — Nexus no inventa un remitente.
 */
@ConfigurationProperties("nexus.notify.smtp")
public record NotifySmtpProperties(
        String host,
        int port,
        String username,
        String password,
        String from
) {
    public NotifySmtpProperties {
        if (port <= 0) {
            port = 587;
        }
    }
}
