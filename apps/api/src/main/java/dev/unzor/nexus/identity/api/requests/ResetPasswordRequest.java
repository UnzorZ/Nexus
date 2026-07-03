package dev.unzor.nexus.identity.api.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reset administrativo de contraseña: el admin fija una nueva contraseña (en
 * claro, que el servicio hashea). Sin flujo de token por correo en B1 (B3).
 */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {
}
