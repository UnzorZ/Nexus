package dev.unzor.nexus.registry.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Latido de una instancia (spec §13.1). {@code instanceId} identifica a la
 * instancia dentro del proyecto; {@code status} es el valor auto-reportado
 * (por defecto "up" si llega vacío); {@code metadata} es libre (clave-valor).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record HeartbeatRequest(
        @NotBlank
        @Size(max = 128)
        String instanceId,

        @NotBlank
        @Size(max = 255)
        String appName,

        @Size(max = 128)
        String appVersion,

        @Size(max = 32)
        String status,

        Map<String, Object> metadata
) {
}
