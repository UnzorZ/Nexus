package dev.unzor.nexus.admin.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de inicio de sesión en el panel Nexus mediante JSON.
 *
 * <p>La contraseña se envía en texto plano. El endpoint de login la verifica
 * contra el mismo {@link org.springframework.security.authentication.AuthenticationManager}
 * que utiliza el formulario HTML de {@code /panel/login}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotBlank
        @Size(max = 128)
        String password
) {
}
