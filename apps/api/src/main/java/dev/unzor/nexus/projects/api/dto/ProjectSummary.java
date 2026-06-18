package dev.unzor.nexus.projects.api.dto;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista resumida de un proyecto para listados, sin el cuerpo de descripción
 * completa ni {@code publicBaseUrl}.
 */
public record ProjectSummary(
        UUID id,
        String slug,
        String name,
        ProjectStatus status,
        Instant createdAt
) {

    public static ProjectSummary from(Project project) {
        return new ProjectSummary(
                project.getId(),
                project.getSlug(),
                project.getName(),
                project.getStatus(),
                project.getCreatedAt()
        );
    }
}
