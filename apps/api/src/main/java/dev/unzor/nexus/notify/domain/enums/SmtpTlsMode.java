package dev.unzor.nexus.notify.domain.enums;

/**
 * Modo de confianza TLS del SMTP de un proyecto (ADR-0013).
 *
 * <ul>
 *   <li>{@code PUBLIC} — se verifica contra el truststore público por defecto
 *       (WebPKI). Es el modo para proveedores públicos (Gmail, SendGrid,
 *       Mailgun, SES) o un servidor propio con certificado de CA pública
 *       (p. ej. Let's Encrypt).</li>
 *   <li>{@code PINNED} — se confía únicamente en la CA que el proyecto sube;
 *       para servidores con certificado self-signed o una CA privada.</li>
 * </ul>
 */
public enum SmtpTlsMode {
    PUBLIC,
    PINNED;

    /** Devuelve el modo, con {@code PUBLIC} por defecto para null/blanco. */
    public static SmtpTlsMode resolve(String value) {
        if (value == null || value.isBlank()) {
            return PUBLIC;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
