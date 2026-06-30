package dev.unzor.nexus.permissions.domain.exception;

/**
 * Lanzada cuando no se encuentra un rol de un proyecto.
 */
public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(String message) {
        super(message);
    }
}
