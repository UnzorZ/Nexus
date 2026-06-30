package dev.unzor.nexus.apikeys.domain.exception;

import java.util.UUID;

/**
 * Lanzada en runtime cuando la API key ha expirado ({@code expires_at} pasada).
 * Lleva la key y el proyecto para que el filtro pueda auditar el rechazo
 * (ADR-0004) sin secretos. Se traduce a {@code 401 api_key_expired} (spec §11).
 */
public class ApiKeyExpiredException extends RuntimeException {

    private final UUID keyId;
    private final UUID projectId;

    public ApiKeyExpiredException(UUID keyId, UUID projectId) {
        super("API key has expired");
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

