package dev.unzor.nexus.permissions.domain.exception;

/**
 * Lanzada al intentar declarar un permiso cuya clave ya existe en el proyecto.
 */
public class PermissionAlreadyExistsException extends RuntimeException {

    public PermissionAlreadyExistsException(String message) {
        super(message);
    }
}
