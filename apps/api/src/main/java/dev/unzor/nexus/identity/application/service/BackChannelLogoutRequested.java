package dev.unzor.nexus.identity.application.service;

import java.util.UUID;

/**
 * Publicado cuando la sesión de un usuario final termina (logout del realm). El listener de
 * back-channel logout lo consume para fan-out de logout tokens a los clientes afectados.
 *
 * @param principalName {@code ProjectUserPrincipal.getName()} (username del realm) — clave
 *                      bajo la que SAS persiste las autorizaciones y {@code sub} del token.
 * @param projectId     proyecto del usuario (filtra clientes al realm correcto).
 * @param issuer        issuer del realm ({@code {origin}/p/{slug}}), {@code iss} del token.
 */
public record BackChannelLogoutRequested(String principalName, UUID projectId, String issuer) {
}
