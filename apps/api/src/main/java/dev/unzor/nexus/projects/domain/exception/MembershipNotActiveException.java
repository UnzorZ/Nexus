package dev.unzor.nexus.projects.domain.exception;

import java.util.UUID;

/**
 * Lanzada al intentar transferir la propiedad a una membresía que no está activa.
 *
 * <p>El invariante del proyecto exige que el destinatario de la propiedad sea una
 * membresía con acceso efectivo: promover a OWNER una membresía pendiente,
 * suspendida o revocada dejaría al proyecto con un propietario sin acceso.</p>
 */
public class MembershipNotActiveException extends RuntimeException {

    private final UUID membershipId;

    public MembershipNotActiveException(UUID membershipId) {
        super("Membership " + membershipId + " is not active and cannot receive ownership");
        this.membershipId = membershipId;
    }

    public UUID getMembershipId() {
        return membershipId;
    }
}
