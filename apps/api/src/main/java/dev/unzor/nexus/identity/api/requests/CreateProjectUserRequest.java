package dev.unzor.nexus.identity.api.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de un usuario final en el realm de un proyecto. La contraseña la fija el
 * admin del proyecto; el usuario nace activo (B1: sin flujo de verificación por
 * correo, que llega en B3).
 */
public record CreateProjectUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 120) String username,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(min = 8, max = 128) String password
) {
}
