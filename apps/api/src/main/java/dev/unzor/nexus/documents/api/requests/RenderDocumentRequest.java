package dev.unzor.nexus.documents.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Cuerpo del render runtime ({@code POST /api/v1/documents/render}). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RenderDocumentRequest(
        @NotBlank @Size(max = 120) String templateName,
        Map<String, String> variables
) {
}
