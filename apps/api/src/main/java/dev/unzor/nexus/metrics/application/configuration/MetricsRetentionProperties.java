package dev.unzor.nexus.metrics.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retención de las métricas de proyecto ({@code nexus.metrics.retention.*}). Un job
 * periódico purga {@code project_metrics} más allá de {@link #retentionDays}. Las
 * métricas son alta-frecuencia (latidos, contadores de app), así que el default es
 * más corto que el del log de auditoría. Poner {@code enabled=false} o
 * {@code retentionDays<=0} desactiva la purga (la tabla crece sin límite — bajo
 * decisión del operador).
 *
 * @param enabled       si el job de retención está activo
 * @param retentionDays días a conservar; los puntos más antiguos se borran
 * @param cron          expresión cron (6 campos, zona local) para el barrido
 */
@ConfigurationProperties(prefix = "nexus.metrics.retention")
public record MetricsRetentionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30") int retentionDays,
        @DefaultValue("0 0 4 * * *") String cron
) {
}
