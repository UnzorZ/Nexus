package dev.unzor.nexus.notify.api.dto;

/** Resultado de previsualizar una plantilla (render sin envío). */
public record RenderedTemplate(
        String subject,
        String body
) {
}
