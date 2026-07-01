package dev.unzor.nexus.documents.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** Cuerpo del render desde el panel ({@code POST .../templates/{templateId}/render}). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RenderTemplateRequest(
        Map<String, String> variables
) {
}
