package dev.unzor.nexus.projects.domain.exception;

import java.util.UUID;

/**
 * Lanzada al intentar transferir la propiedad a una membresía que ya es OWNER.
 */
public class MembershipAlreadyOwnerException extends RuntimeException {

    private final UUID membershipId;

    public MembershipAlreadyOwnerException(UUID membershipId) {
        super("Membership " + membershipId + " is already the project owner");
        this.membershipId = membershipId;
    }

    public UUID getMembershipId() {
        return membershipId;
    }
}
