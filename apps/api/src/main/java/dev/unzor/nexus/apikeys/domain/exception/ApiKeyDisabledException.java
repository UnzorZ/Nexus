package dev.unzor.nexus.apikeys.domain.exception;

import java.util.UUID;

/**
 * Lanzada en runtime cuando la API key está deshabilitada. Lleva la key y el
 * proyecto para que el filtro pueda auditar el rechazo (ADR-0004) sin secretos.
 * Se traduce a {@code 401 api_key_disabled} (spec §11).
 */
public class ApiKeyDisabledException extends RuntimeException {

    private final UUID keyId;
    private final UUID projectId;

    public ApiKeyDisabledException(UUID keyId, UUID projectId) {
        super("API key is disabled");
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

