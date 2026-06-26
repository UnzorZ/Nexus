package dev.unzor.nexus.projects.api.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mass-assignment-safe by construction: it is a {@code record} with a fixed
 * component list, so {@code slug} and {@code status} can never be set from the
 * request regardless of Jackson's unknown-property handling.
 */
public record UpdateProjectRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @Size(max = 1000)
        String description,

        @Size(max = 2048)
        @HttpsUrl
        String publicBaseUrl
) {
}
