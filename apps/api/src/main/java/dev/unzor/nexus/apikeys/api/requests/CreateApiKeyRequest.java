package dev.unzor.nexus.apikeys.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Crea una API key. Los scopes se validan por formato ({@link ApiKeyScope});
 * {@code expiresAt} es opcional (null = sin expiración).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateApiKeyRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        List<@NotNull @ApiKeyScope @Size(max = 64) String> scopes,

        Instant expiresAt
) {
}
