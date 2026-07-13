package dev.unzor.nexus.identity.application.service;

import java.util.UUID;
import java.util.List;

/**
 * Publicado cuando la sesión de un usuario final termina (logout del realm). El listener de
 * back-channel logout lo consume para fan-out de logout tokens a los clientes afectados.
 *
 * @param principalName {@code ProjectUserPrincipal.getName()} (sujeto OIDC) — clave
 *                      bajo la que SAS persiste las autorizaciones y {@code sub} del token.
 * @param projectId     proyecto del usuario (filtra clientes al realm correcto).
 * @param issuer        issuer del realm ({@code {origin}/p/{slug}}), {@code iss} del token.
 * @param targets       snapshot opcional de destinos capturado antes de revocar grants.
 *                      Vacío indica que el listener debe resolverlos desde las autorizaciones.
 */
public record BackChannelLogoutRequested(
        String principalName,
        UUID projectId,
        String issuer,
        List<BackChannelLogoutTarget> targets
) {
    public BackChannelLogoutRequested(String principalName, UUID projectId, String issuer) {
        this(principalName, projectId, issuer, List.of());
    }

    public BackChannelLogoutRequested {
        targets = List.copyOf(targets);
    }
}
