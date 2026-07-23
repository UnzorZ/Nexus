package dev.unzor.nexus.identity.api.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create or update the per-project Google IdP configuration. {@code clientSecret}
 * is required when creating the configuration; on update it may be blank, in which case the
 * existing secret is kept.
 */
public record SaveGoogleIdpRequest(
        @NotBlank @Size(max = 255) String clientId,
        @Size(max = 2048) String clientSecret,
        @Size(max = 255) String issuer,
        @Size(max = 255) String scope,
        boolean enabled,
        boolean autoProvision
) {
}
