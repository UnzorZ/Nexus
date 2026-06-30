package dev.unzor.nexus.projects.domain.exception;

import java.util.UUID;

/**
 * Lanzada cuando se referencia una membresía que no existe dentro de un proyecto.
 */
public class MembershipNotFoundException extends RuntimeException {

    private final UUID membershipId;

    public MembershipNotFoundException(UUID membershipId) {
        super("Membership not found: " + membershipId);
        this.membershipId = membershipId;
    }

    public UUID getMembershipId() {
        return membershipId;
    }
}
