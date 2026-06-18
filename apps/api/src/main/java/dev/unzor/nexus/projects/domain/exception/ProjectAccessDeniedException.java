package dev.unzor.nexus.projects.domain.exception;

import java.util.UUID;

/**
 * Lanzada cuando una cuenta Nexus intenta acceder a un proyecto sin ser
 * miembro activa ni administradora de instancia.
 */
public class ProjectAccessDeniedException extends RuntimeException {

    private final UUID projectId;
    private final UUID accountId;

    public ProjectAccessDeniedException(UUID projectId, UUID accountId) {
        super("Account " + accountId + " does not have access to project " + projectId);
        this.projectId = projectId;
        this.accountId = accountId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getAccountId() {
        return accountId;
    }
}
