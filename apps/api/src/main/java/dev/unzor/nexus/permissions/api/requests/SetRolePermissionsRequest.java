package dev.unzor.nexus.permissions.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Reemplaza el conjunto completo de claves de permiso asignadas a un rol
 * (semántica PUT). Cada clave se valida con {@link PermissionKey} (formato +
 * comodines terminales {@code orders.*}, {@code *}) y se acota a 128 caracteres,
 * el tamaño de la columna {@code permission_key}.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SetRolePermissionsRequest(
        @NotNull
        List<@NotNull @PermissionKey @Size(max = 128) String> permissionKeys
) {
}
