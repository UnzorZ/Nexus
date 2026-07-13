package dev.unzor.nexus.projects.application;

import java.util.UUID;

/**
 * Publicado dentro de la transacción que materializa la transición de un proyecto
 * a {@code ARCHIVED}.
 */
public record ProjectArchived(UUID projectId) {
}
