package io.nexus.client.internal;

import io.nexus.client.api.MetricPoint;

import java.util.Map;

/**
 * Cliente crudo de métricas del API de proyecto: reporte push de un punto
 * ({@code POST /api/v1/metrics/record}, scope {@code metrics:write}). El lado
 * <i>pull</i> (exposición Prometheus para que un servidor la scrapee) es un
 * endpoint del backend, no del SDK.
 */
public class MetricsClient {

    private final NexusHttpClient http;

    public MetricsClient(NexusHttpClient http) {
        this.http = http;
    }

    /** Registra un punto de métrica con tags opcionales. */
    public MetricPoint record(String name, double value, Map<String, String> tags) {
        return http.post("/api/v1/metrics/record",
                Map.of("name", name, "value", value, "tags", tags == null ? Map.of() : tags),
                MetricPoint.class);
    }

    /** Registra un punto de métrica sin tags. */
    public MetricPoint record(String name, double value) {
        return record(name, value, Map.of());
    }
}
