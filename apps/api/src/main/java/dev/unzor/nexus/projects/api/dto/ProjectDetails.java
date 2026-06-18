package dev.unzor.nexus.projects.api.dto;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista completa e inmutable de un proyecto, sin exponer la entidad JPA.
 */
public record ProjectDetails(
        UUID id,
        String slug,
        String name,
        String description,
        ProjectStatus status,
        String publicBaseUrl,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectDetails from(Project project) {
        return new ProjectDetails(
                project.getId(),
                project.getSlug(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getPublicBaseUrl(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
