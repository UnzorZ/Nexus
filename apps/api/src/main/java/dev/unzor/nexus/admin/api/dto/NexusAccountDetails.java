package dev.unzor.nexus.admin.api.dto;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.domain.enums.NexusAccountStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista inmutable y segura de una cuenta Nexus.
 *
 * <p>Se utiliza fuera de la capa de persistencia para no exponer la entidad JPA ni
 * campos sensibles como {@code passwordHash}. Si la entidad cambia dentro de una
 * transacción, esta vista no cambia automáticamente.</p>
 */
public record NexusAccountDetails(
        UUID id,
        String email,
        String displayName,
        NexusAccountStatus status,
        boolean mfaEnabled,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Convierte la entidad persistida en un modelo de lectura apto para devolver a
     * otros servicios o controladores.
     */
    public static NexusAccountDetails from(NexusAccount account) {
        return new NexusAccountDetails(
                account.getId(),
                account.getEmail(),
                account.getDisplayName(),
                account.getStatus(),
                account.isMfaEnabled(),
                account.getEmailVerifiedAt(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
