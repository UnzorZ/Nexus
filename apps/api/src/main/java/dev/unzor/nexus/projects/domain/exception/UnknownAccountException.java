package dev.unzor.nexus.projects.domain.exception;

/**
 * Lanzada cuando se intenta invitar a una dirección de email sin cuenta Nexus
 * asociada.
 */
public class UnknownAccountException extends RuntimeException {

    private final String email;

    public UnknownAccountException(String email) {
        super("No NexusAccount with that address: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
