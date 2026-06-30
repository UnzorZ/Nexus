package dev.unzor.nexus.permissions.api.dto;

import dev.unzor.nexus.permissions.domain.entity.ProjectRole;

import java.util.List;
import java.util.UUID;

/**
 * Vista de un rol de un proyecto, incluyendo las claves de permiso que tiene
 * asignadas.
 */
public record RoleDetails(
        UUID id,
        String key,
        String label,
        String description,
        boolean system,
        List<String> permissionKeys
) {
    public static RoleDetails from(ProjectRole role, List<String> permissionKeys) {
        return new RoleDetails(
                role.getId(),
                role.getKey(),
                role.getLabel(),
                role.getDescription(),
                role.isSystem(),
                List.copyOf(permissionKeys)
        );
    }
}
