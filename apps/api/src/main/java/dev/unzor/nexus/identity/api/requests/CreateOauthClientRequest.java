package dev.unzor.nexus.identity.api.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Alta de un cliente OAuth de proyecto. {@code confidential=true} genera un
 * secreto (verificado con Basic auth); {@code confidential=false} es público y
 * obliga PKCE. Las redirect URIs se validan por coincidencia exacta en el AS.
 */
public record CreateOauthClientRequest(
        @NotBlank @Size(max = 200) String name,
        @NotEmpty List<@Size(max = 500) String> redirectUris,
        List<@Size(max = 500) String> postLogoutRedirectUris,
        List<@Size(max = 64) String> grantTypes,
        List<@Size(max = 64) String> scopes,
        boolean requirePkce,
        boolean confidential,
        boolean consentRequired,
        @Size(max = 500) String backchannelLogoutUri
) {
}
