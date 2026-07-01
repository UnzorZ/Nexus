package dev.unzor.nexus.notify.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Cuerpo del envío de prueba desde el panel ({@code POST .../notify/test}). Se
 * necesita {@code templateName} o bien {@code subject}+{@code body}.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SendTestNotificationRequest(
        @NotBlank @Email @Size(max = 320) String to,
        @Size(max = 120) String templateName,
        @Size(max = 200) String subject,
        @Size(max = 20000) String body,
        Map<String, String> variables
) {
}
