package dev.unzor.nexus.permissions.api.dto;

import dev.unzor.nexus.permissions.domain.entity.ProjectPermission;
import dev.unzor.nexus.permissions.domain.enums.PermissionSource;

import java.util.UUID;

/**
 * Vista de un permiso del catálogo de un proyecto.
 */
public record PermissionDetails(
        UUID id,
        String key,
        String label,
        String description,
        PermissionSource source
) {
    public static PermissionDetails from(ProjectPermission permission) {
        return new PermissionDetails(
                permission.getId(),
                permission.getKey(),
                permission.getLabel(),
                permission.getDescription(),
                permission.getSource()
        );
    }
}
