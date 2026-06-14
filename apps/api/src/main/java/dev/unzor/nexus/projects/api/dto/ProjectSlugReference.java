package dev.unzor.nexus.projects.api.dto;

import java.util.UUID;

public record ProjectSlugReference(UUID projectId, String projectSlug) {
}
