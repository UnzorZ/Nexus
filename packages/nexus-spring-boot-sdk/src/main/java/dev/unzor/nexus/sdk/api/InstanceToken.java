package dev.unzor.nexus.sdk.api;

import java.util.UUID;

/**
 * Instance token efímero del handshake del SDK ({@code POST /api/v1/registry/register}).
 */
public record InstanceToken(String token, String tokenType, long expiresInSeconds, UUID projectId) {
}
