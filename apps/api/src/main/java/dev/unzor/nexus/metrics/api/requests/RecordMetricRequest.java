package dev.unzor.nexus.metrics.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Cuerpo del reporte de un punto de métrica ({@code POST /api/v1/metrics/record}). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RecordMetricRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull Double value,
        Map<String, String> tags
) {
}
