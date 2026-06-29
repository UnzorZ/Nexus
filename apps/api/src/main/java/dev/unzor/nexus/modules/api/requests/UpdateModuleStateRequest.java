package dev.unzor.nexus.modules.api.requests;

import jakarta.validation.constraints.NotNull;

/**
 * Carga para activar o desactivar un módulo de proyecto.
 */
public record UpdateModuleStateRequest(@NotNull Boolean enabled) {
}
