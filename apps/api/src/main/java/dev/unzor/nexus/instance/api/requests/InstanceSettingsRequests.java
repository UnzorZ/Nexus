package dev.unzor.nexus.instance.api.requests;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpos de los PUT de configuración de instancia. {@code modules} y los
 * segundos de heartbeat admiten {@code null} (reset / clear).
 */
public final class InstanceSettingsRequests {

    private InstanceSettingsRequests() {
    }

    public record SaveRegistrationRequest(@NotNull Boolean open) {
    }

    /** {@code modules=null} resetea a los defaults del catálogo; vacío = ninguno. */
    public record SaveDefaultModulesRequest(java.util.List<String> modules) {
    }

    /** Ambos null = clear; ambos presentes = override. */
    public record SaveHeartbeatDefaultsRequest(Integer intervalSeconds, Integer timeoutSeconds) {
    }
}
