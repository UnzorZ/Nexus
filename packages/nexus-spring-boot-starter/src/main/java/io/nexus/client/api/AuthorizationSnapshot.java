package io.nexus.client.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot de autorización de un usuario (spec §14.11), espejo del DTO del
 * backend {@code GET /api/v1/authz/users/{userId}/snapshot}.
 */
public record AuthorizationSnapshot(
        UUID userId,
        UUID projectId,
        long authzVersion,
        List<String> roles,
        List<String> permissions,
        Instant expiresAt
) {
}
