package dev.unzor.nexus.notify.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del guardado de SMTP del panel ({@code PUT .../notify/smtp}). La
 * contraseña es opcional: si llega vacía/nula en una actualización se conserva
 * la existente.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SaveSmtpSettingsRequest(
        @NotBlank @Size(max = 255) String host,
        @NotNull @Min(1) Integer port,
        @Size(max = 255) String username,
        @NotBlank @Size(max = 255) String from,
        @Size(max = 20000) String password
) {
}
