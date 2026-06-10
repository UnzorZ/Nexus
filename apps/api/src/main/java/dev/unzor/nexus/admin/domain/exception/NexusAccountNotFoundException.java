package dev.unzor.nexus.admin.domain.exception;

import java.util.UUID;

/**
 * Indica que no existe una cuenta Nexus para el identificador solicitado.
 */
public class NexusAccountNotFoundException extends RuntimeException {

    public NexusAccountNotFoundException(UUID accountId) {
        super("Nexus account not found: " + accountId);
    }
}
