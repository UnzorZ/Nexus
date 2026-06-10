package dev.unzor.nexus.identity.domain.enums;

/**
 * Estado operativo de un usuario dentro del realm de un proyecto.
 *
 * <p>El estado está aislado por proyecto y no afecta a cuentas Nexus ni a usuarios
 * con el mismo email pertenecientes a otros proyectos.</p>
 */
public enum ProjectUserStatus {
    /** Usuario creado que todavía no ha verificado su email. */
    PENDING_VERIFICATION,

    /** Usuario habilitado para autenticarse en su proyecto. */
    ACTIVE,

    /** Usuario bloqueado temporalmente dentro del proyecto. */
    SUSPENDED,

    /** Usuario desactivado de forma indefinida dentro del proyecto. */
    DISABLED
}
