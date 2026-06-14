package dev.unzor.nexus.admin.domain.exception;

public class NexusAccountEmailAlreadyExistsException extends RuntimeException {

    private final String email;

    public NexusAccountEmailAlreadyExistsException(String email) {
        super("A Nexus account already exists for email: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
