package dev.unzor.nexus.metrics.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Serie agregada por (nombre + tags): último valor, nº de puntos recientes y los
 * puntos (asc). Cada tagset es su propia serie (M7c2) para no mezclar líneas de
 * distinta cardinalidad en el mismo gráfico del panel.
 */
public record MetricSeries(
        String name,
        Map<String, String> tags,
        double lastValue,
        Instant lastRecordedAt,
        int pointCount,
        List<MetricPoint> points
) {
}
