package dev.unzor.nexus.metrics.api.dto;

import dev.unzor.nexus.metrics.domain.entity.ProjectMetric;

import java.time.Instant;
import java.util.Map;

/** Punto individual de una serie de métricas. */
public record MetricPoint(double value, Map<String, String> tags, Instant recordedAt) {
    public static MetricPoint from(ProjectMetric metric) {
        return new MetricPoint(metric.getValue(), metric.getTags(), metric.getRecordedAt());
    }
}
