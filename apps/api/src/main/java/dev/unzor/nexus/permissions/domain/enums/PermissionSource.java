package dev.unzor.nexus.permissions.domain.enums;

/**
 * Origen de un permiso declarado en el catálogo de un proyecto.
 *
 * <p>En el MVP solo se persiste {@link #WEB} (permisos creados manualmente desde
 * el panel). El resto de orígenes queda reservado para la futura sincronización
 * declarativa desde aplicaciones (YAML/CODE/OPENAPI) y permisos del sistema.</p>
 */
public enum PermissionSource {
    /** Permiso creado manualmente desde el panel. */
    WEB,

    /** Declarado en un fichero YAML de la aplicación. */
    YAML,

    /** Declarado en el código de la aplicación. */
    CODE,

    /** Declarado en una especificación OpenAPI. */
    OPENAPI,

    /** Permiso interno del sistema Nexus. */
    SYSTEM
}
