package dev.unzor.nexus.projects.domain.exception;

public class ProjectAlreadyExistException extends RuntimeException {
    private final String slug;

    public ProjectAlreadyExistException(String slug) {
        super("A project already exists with slug: " + slug);
        this.slug = slug;
    }

    public String getSlug() {
        return slug;
    }
}