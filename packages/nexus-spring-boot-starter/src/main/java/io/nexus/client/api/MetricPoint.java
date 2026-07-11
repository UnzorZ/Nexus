package io.nexus.client.api;

import java.time.Instant;
import java.util.Map;

/**
 * Punto de métrica confirmado por Nexus ({@code POST /api/v1/metrics/record},
 * scope {@code metrics:write}).
 */
public record MetricPoint(double value, Map<String, String> tags, Instant recordedAt) {
}
