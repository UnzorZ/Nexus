package dev.unzor.nexus.apikeys.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.unzor.nexus.apikeys.domain.enums.ApiKeyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Actualiza los campos editables de una API key: nombre, estado (activar /
 * deshabilitar) y expiración. La key/secret no son editables aquí (usa rotación).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateApiKeyRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        ApiKeyStatus status,

        Instant expiresAt
) {
}
