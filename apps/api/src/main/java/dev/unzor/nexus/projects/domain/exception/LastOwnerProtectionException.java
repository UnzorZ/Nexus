package dev.unzor.nexus.projects.domain.exception;

import java.util.UUID;

/**
 * Lanzada cuando una operación dejaría al proyecto sin ningún miembro OWNER
 * activo.
 */
public class LastOwnerProtectionException extends RuntimeException {

    private final UUID projectId;

    public LastOwnerProtectionException(UUID projectId) {
        super("Project " + projectId + " must keep at least one active OWNER");
        this.projectId = projectId;
    }

    public UUID getProjectId() {
        return projectId;
    }
}
