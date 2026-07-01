package dev.unzor.nexus.registry.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo del guardado de umbrales de liveness ({@code PUT .../heartbeats/settings}).
 * Se valida {@code 1 <= intervalSeconds <= staleAfterSeconds <= timeoutSeconds}.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SaveRegistrySettingsRequest(
        @NotNull @Min(1) @Max(86400) Integer intervalSeconds,
        @NotNull @Min(1) @Max(86400) Integer staleAfterSeconds,
        @NotNull @Min(1) @Max(86400) Integer timeoutSeconds
) {
}
