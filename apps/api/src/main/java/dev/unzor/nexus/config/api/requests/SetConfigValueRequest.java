package dev.unzor.nexus.config.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.unzor.nexus.config.domain.enums.ConfigValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del upsert de un valor de configuración ({@code PUT /{key}}). La clave
 * va en el path; el cuerpo lleva el valor y su tipo.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SetConfigValueRequest(
        @NotBlank @Size(max = 4000) String value,
        @NotNull ConfigValueType valueType
) {
}
