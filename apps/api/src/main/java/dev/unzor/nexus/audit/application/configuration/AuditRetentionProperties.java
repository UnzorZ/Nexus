package dev.unzor.nexus.audit.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retención del log de auditoría ({@code nexus.audit.retention.*}). Un job
 * periódico purga {@code audit_log} más allá de {@link #retentionDays}. Poner
 * {@code enabled=false} o {@code retentionDays<=0} desactiva la purga (tabla
 * crece sin límite — bajo decisión del operador).
 *
 * @param enabled       si el job de retención está activo
 * @param retentionDays días a conservar; las entradas más antiguas se borran
 * @param cron          expresión cron (6 campos, zona local) para el barrido
 */
@ConfigurationProperties(prefix = "nexus.audit.retention")
public record AuditRetentionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("90") int retentionDays,
        @DefaultValue("0 30 3 * * *") String cron
) {
}
