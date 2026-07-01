package dev.unzor.nexus.notify.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Cuerpo del envío runtime ({@code POST /api/v1/notify/send}). Se requiere
 * {@code templateName} (renderiza la plantilla con {@code variables}) o, en su
 * defecto, {@code subject} + {@code body} en línea.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SendNotificationRequest(
        @NotBlank @Size(max = 320) String to,
        @Size(max = 120) String templateName,
        @Size(max = 200) String subject,
        @Size(max = 20000) String body,
        Map<String, String> variables
) {
}
