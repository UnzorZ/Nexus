package dev.unzor.nexus.permissions.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Un permiso declarado por una aplicación vía el API de proyecto
 * (POST /api/v1/permissions/declare, spec §18 SDK). La {@code key} es el
 * identificador estable del permiso (p. ej. {@code orders.read}); el
 * {@code label} es opcional y descriptivo.
 */
public record PermissionDeclaration(
        @NotBlank @Size(max = 120) String key,
        @Size(max = 120) String label
) {
}
