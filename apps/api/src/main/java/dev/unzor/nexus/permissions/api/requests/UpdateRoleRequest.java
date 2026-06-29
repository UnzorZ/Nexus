package dev.unzor.nexus.permissions.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Actualiza los metadatos mostrables de un rol. La clave y el flag
 * {@code system} no son editables.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateRoleRequest(
        @NotBlank
        @Size(max = 120)
        String label,

        @Size(max = 1000)
        String description
) {
}
