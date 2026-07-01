package dev.unzor.nexus.documents.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de create/update de plantilla (POST y PATCH comparten forma). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record DocumentTemplateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String contentType,
        @NotBlank @Size(max = 20000) String templateBody
) {
}
