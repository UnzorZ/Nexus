package dev.unzor.nexus.identity.api.dto;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de un {@code ProjectUser} para el panel. Nunca expone el hash de la
 * contraseña ({@code password_hash}); el {@code status} se serializa como el
 * nombre del enum.
 */
public record ProjectUserDetails(
        UUID id,
        String email,
        String username,
        String displayName,
        String status,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        Instant createdAt
) {

    public static ProjectUserDetails from(ProjectUser user) {
        return new ProjectUserDetails(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus().name(),
                user.getEmailVerifiedAt(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
