package dev.unzor.nexus.permissions.domain.exception;

/**
 * Lanzada al intentar crear un rol cuya clave ya existe en el proyecto.
 */
public class RoleAlreadyExistsException extends RuntimeException {

    public RoleAlreadyExistsException(String message) {
        super(message);
    }
}
