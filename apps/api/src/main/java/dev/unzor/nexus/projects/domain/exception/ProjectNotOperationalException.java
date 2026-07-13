package dev.unzor.nexus.projects.domain.exception;

import dev.unzor.nexus.projects.domain.enums.ProjectStatus;

import java.util.UUID;

public class ProjectNotOperationalException extends RuntimeException {

    private final UUID projectId;
    private final ProjectStatus status;

    public ProjectNotOperationalException(UUID projectId, ProjectStatus status) {
        super("Project is not operational: " + projectId + " (status: " + status + ")");
        this.projectId = projectId;
        this.status = status;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public ProjectStatus getStatus() {
        return status;
    }
}
