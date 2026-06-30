package dev.unzor.nexus.permissions.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Crea un rol en el proyecto. La clave es un slug estable e inmutable.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateRoleRequest(
        @NotBlank
        @Size(max = 128)
        @Pattern(
                regexp = "^[a-z0-9][a-z0-9_-]{0,127}$",
                message = "must contain only lowercase letters, numbers, underscores and hyphens"
        )
        String key,

        @NotBlank
        @Size(max = 120)
        String label,

        @Size(max = 1000)
        String description
) {
}
