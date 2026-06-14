package dev.unzor.nexus.admin.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de alta de una cuenta Nexus.
 *
 * <p>La contraseña llega en texto plano y se hashea en el servicio de aplicación.
 * Campos como {@code passwordHash} se rechazan explícitamente para impedir que un
 * cliente defina el hash almacenado.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateNexusAccountRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotBlank
        @Size(min = 8, max = 128)
        String password,

        @NotBlank
        @Size(max = 120)
        String displayName
) {
}
