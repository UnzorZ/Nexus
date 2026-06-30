package dev.unzor.nexus.permissions.domain.exception;

/**
 * Lanzada cuando no se encuentra un permiso del catálogo de un proyecto.
 */
public class PermissionNotFoundException extends RuntimeException {

    public PermissionNotFoundException(String message) {
        super(message);
    }
}
