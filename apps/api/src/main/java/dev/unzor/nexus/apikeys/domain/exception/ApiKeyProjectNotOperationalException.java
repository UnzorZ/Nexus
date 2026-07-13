package dev.unzor.nexus.apikeys.domain.exception;

import java.util.UUID;

/**
 * Rechazo de una API key válida cuyo proyecto no admite operaciones.
 * Conserva ambos identificadores para que el filtro pueda auditar la
 * credencial concreta sin exponer su secreto.
 */
public class ApiKeyProjectNotOperationalException extends RuntimeException {

    private final UUID keyId;
    private final UUID projectId;

    public ApiKeyProjectNotOperationalException(UUID keyId, UUID projectId, Throwable cause) {
        super("The project owning the API key is not operational", cause);
        this.keyId = keyId;
        this.projectId = projectId;
    }

    public UUID getKeyId() {
        return keyId;
    }

    public UUID getProjectId() {
        return projectId;
    }
}
