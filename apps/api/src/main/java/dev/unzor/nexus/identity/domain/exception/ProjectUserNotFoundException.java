package dev.unzor.nexus.identity.domain.exception;

import java.util.UUID;

/**
 * Se lanza cuando no existe un {@code ProjectUser} para el par
 * {@code (projectId, userId)} indicado. El repositorio es siempre project-scoped,
 * así que esta excepción también cubre el cruce accidental entre realms.
 */
public class ProjectUserNotFoundException extends RuntimeException {

    public ProjectUserNotFoundException(UUID projectId, UUID userId) {
        super("Project user not found: projectId=" + projectId + ", userId=" + userId);
    }
}
