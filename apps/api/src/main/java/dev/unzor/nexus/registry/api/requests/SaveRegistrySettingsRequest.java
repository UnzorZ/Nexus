package dev.unzor.nexus.registry.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del guardado de umbrales de liveness + (opcional) alerta offline
 * ({@code PUT .../heartbeats/settings}). Se valida {@code 1 <= intervalSeconds
 * <= timeoutSeconds}. Los campos {@code offlineNotify*} son opcionales: si se
 * omite {@code offlineNotifyEnabled}, el servicio preserva la config existente
 * (así el guardado de sólo umbrales no la resetea). Si viene {@code true}, el
 * servicio exige un {@code offlineNotifyEmail} válido.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SaveRegistrySettingsRequest(
        @NotNull @Min(1) @Max(86400) Integer intervalSeconds,
        @NotNull @Min(1) @Max(86400) Integer timeoutSeconds,
        Boolean offlineNotifyEnabled,
        @Size(max = 320) String offlineNotifyEmail
) {
}
