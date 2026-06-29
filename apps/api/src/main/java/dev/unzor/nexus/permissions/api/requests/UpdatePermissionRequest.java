package dev.unzor.nexus.permissions.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Actualiza los metadatos mostrables de un permiso. La clave no es editable.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdatePermissionRequest(
        @NotBlank
        @Size(max = 120)
        String label,

        @Size(max = 1000)
        String description
) {
}
