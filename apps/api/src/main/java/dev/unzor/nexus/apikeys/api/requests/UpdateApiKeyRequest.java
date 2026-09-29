package dev.unzor.nexus.apikeys.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.unzor.nexus.apikeys.domain.enums.ApiKeyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Actualiza los campos editables de una API key: nombre, estado (activar /
 * deshabilitar), expiración y scopes. {@code scopes} es opcional: {@code null}
 * deja los actuales (clientes que no lo envían). La key/secret no son editables
 * aquí (usa rotación).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateApiKeyRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        ApiKeyStatus status,

        Instant expiresAt,

        List<@NotNull @ApiKeyScope @Size(max = 64) String> scopes
) {
}
