package dev.unzor.nexus.admin.domain.enums;

/**
 * Estado operativo de una cuenta del panel de Nexus.
 *
 * <p>Este estado solo controla la cuenta Nexus. No modifica membresías de proyecto
 * ni usuarios OAuth que puedan utilizar el mismo email.</p>
 */
public enum NexusAccountStatus {
    /** Cuenta creada que todavía no ha verificado su email. */
    PENDING_VERIFICATION,

    /** Cuenta habilitada para autenticarse en Nexus. */
    ACTIVE,

    /** Cuenta bloqueada temporalmente por una decisión administrativa. */
    SUSPENDED,

    /** Cuenta desactivada de forma indefinida. */
    DISABLED
}
