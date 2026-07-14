package dev.unzor.nexus.sdk.security;

/**
 * Publicado por {@link NexusBackChannelLogoutController} cuando Nexus envía un
 * logout token válido (OIDC back-channel logout, RFC 8417). La app lo escucha
 * para invalidar la sesión local del {@code sub} (p. ej. vía Spring Session).
 */
public record NexusBackChannelLogoutEvent(String sub, String iss) {
}
