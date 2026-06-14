package dev.unzor.nexus.admin.domain.exception;

import java.util.UUID;

public class NexusAccountNotFoundException extends RuntimeException {

    public NexusAccountNotFoundException(UUID accountId) {
        super("Nexus account not found: " + accountId);
    }
}
