package dev.unzor.nexus.identity.domain.exception;

/**
 * El proyecto no tiene habilitado el registro público (self-signup); sólo invitación
 * administrativa.
 */
public class PublicRegistrationDisabledException extends RuntimeException {
    public PublicRegistrationDisabledException() {
        super("Public registration is disabled for this project.");
    }
}
