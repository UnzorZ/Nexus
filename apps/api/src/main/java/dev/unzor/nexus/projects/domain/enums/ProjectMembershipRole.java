package dev.unzor.nexus.projects.domain.enums;

/**
 * Nivel de capacidad de una cuenta Nexus dentro de un proyecto.
 *
 * <p>El rol expresa qué puede hacer la cuenta. El estado operativo de la relación
 * se controla por separado mediante {@link ProjectMembershipStatus}.</p>
 */
public enum ProjectMembershipRole {
    /** Control total del proyecto, incluida su propiedad y sus membresías. */
    OWNER,

    /** Gestión operativa del proyecto y de sus recursos. */
    ADMIN,

    /** Acceso limitado según las políticas de proyecto que se definan. */
    MEMBER
}
