package dev.unzor.nexus.vault.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de create/rotate de un secreto ({@code POST|PATCH /vault/secrets/{key}}). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record WriteSecretRequest(@NotBlank @Size(max = 20000) String value) {
}
