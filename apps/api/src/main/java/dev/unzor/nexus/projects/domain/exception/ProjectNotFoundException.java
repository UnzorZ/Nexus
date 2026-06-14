package dev.unzor.nexus.projects.domain.exception;

public class ProjectNotFoundException extends RuntimeException {

    private final String projectSlug;

    public ProjectNotFoundException(String projectSlug) {
        super("Project not found: " + projectSlug);
        this.projectSlug = projectSlug;
    }

    public String getProjectSlug() {
        return projectSlug;
    }
}
