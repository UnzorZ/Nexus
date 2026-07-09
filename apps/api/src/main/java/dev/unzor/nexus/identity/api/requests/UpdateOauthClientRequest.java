package dev.unzor.nexus.identity.api.requests;

import dev.unzor.nexus.identity.domain.enums.OauthClientStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Actualización de un cliente OAuth (nombre, redirect URIs, scopes, estado).
 * No permite rotar el secreto desde aquí (usa el endpoint de rotate).
 */
public record UpdateOauthClientRequest(
        @NotBlank @Size(max = 200) String name,
        @NotEmpty List<@Size(max = 500) String> redirectUris,
        List<@Size(max = 500) String> postLogoutRedirectUris,
        List<@Size(max = 64) String> scopes,
        @NotNull OauthClientStatus status,
        @Size(max = 500) String backchannelLogoutUri
) {
}
