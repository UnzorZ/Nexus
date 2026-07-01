package dev.unzor.nexus.metrics.api.dto;

import java.time.Instant;
import java.util.List;

/** Serie agregada por nombre: último valor, nº de puntos recientes y los puntos (asc). */
public record MetricSeries(
        String name,
        double lastValue,
        Instant lastRecordedAt,
        int pointCount,
        List<MetricPoint> points
) {
}
