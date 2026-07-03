package dev.unzor.nexus.identity.domain.exception;

/**
 * Ya existe un cliente OAuth con el {@code client_id} indicado (los client_id son
 * globalmente únicos, spec §9.6).
 */
public class OauthClientIdAlreadyExistsException extends RuntimeException {

    private final String clientId;

    public OauthClientIdAlreadyExistsException(String clientId) {
        super("OAuth client already exists: clientId=" + clientId);
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }
}
