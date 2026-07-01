package dev.unzor.nexus.notify.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** Cuerpo de la previsualización de plantilla ({@code POST .../templates/{id}/preview}). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PreviewTemplateRequest(
        Map<String, String> variables
) {
}
