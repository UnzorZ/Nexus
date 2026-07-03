package dev.unzor.nexus.identity.domain.exception;

import java.util.UUID;

/**
 * No existe un cliente OAuth para el par {@code (projectId, clientId)} indicado.
 */
public class OauthClientNotFoundException extends RuntimeException {

    public OauthClientNotFoundException(UUID projectId, UUID clientId) {
        super("OAuth client not found: projectId=" + projectId + ", clientId=" + clientId);
    }
}
