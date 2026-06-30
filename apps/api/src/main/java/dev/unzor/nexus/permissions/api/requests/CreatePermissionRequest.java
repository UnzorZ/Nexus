package dev.unzor.nexus.permissions.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Crea un permiso en el catálogo del proyecto. La clave la define el usuario y
 * se valida por formato; es inmutable tras la creación.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreatePermissionRequest(
        @NotBlank
        @Size(max = 128)
        @PermissionKey
        String key,

        @NotBlank
        @Size(max = 120)
        String label,

        @Size(max = 1000)
        String description
) {
}
