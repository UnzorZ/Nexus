package dev.unzor.nexus.vault.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la rotación de master key ({@code POST .../vault/master-key}). La
 * nueva clave se aplica al proyecto: se re-cifran todos sus secretos y se guarda
 * como override (envuelta con la master key global).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RotateMasterKeyRequest(
        @NotBlank @Size(min = 8, max = 20000) String masterKey
) {
}
