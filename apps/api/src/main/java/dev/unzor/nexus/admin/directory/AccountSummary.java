package dev.unzor.nexus.admin.directory;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista resumida e inmutable de una cuenta Nexus, publicada a otros módulos
 * (p. ej. {@code projects}) para resolver destinatarios de invitación y mostrar
 * miembros sin exponer la entidad JPA ni datos sensibles como
 * {@code passwordHash}.
 */
public record AccountSummary(
        UUID id,
        String email,
        String displayName,
        boolean mfaEnabled,
        Instant lastLoginAt
) {

    public static AccountSummary from(NexusAccount account) {
        return new AccountSummary(
                account.getId(),
                account.getEmail(),
                account.getDisplayName(),
                account.isMfaEnabled(),
                account.getLastLoginAt()
        );
    }
}
