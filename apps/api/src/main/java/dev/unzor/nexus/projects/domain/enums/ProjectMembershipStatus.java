package dev.unzor.nexus.projects.domain.enums;

public enum ProjectMembershipStatus {
    /** Invitación creada pero todavía no aceptada. */
    INVITED,

    /** La membresía concede acceso al proyecto. */
    ACTIVE,

    /** Acceso bloqueado temporalmente sin eliminar la relación. */
    SUSPENDED,

    /** Acceso retirado de forma explícita. */
    REVOKED
}
