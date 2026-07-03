package dev.unzor.nexus.identity.api.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Actualización del perfil (nombre público + username). No permite cambiar
 * estado ni contraseña desde aquí.
 */
public record UpdateProjectUserRequest(
        @NotBlank @Size(max = 120) String displayName,
        @Size(max = 120) String username
) {
}
