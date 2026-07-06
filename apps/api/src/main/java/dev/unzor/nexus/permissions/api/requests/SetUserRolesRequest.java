package dev.unzor.nexus.permissions.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Reemplaza el conjunto completo de roles asignados a un usuario de proyecto
 * (semántica PUT). Los ids de rol se validan como UUID; la pertenencia al
 * proyecto y la existencia de cada rol se comprueban en el servicio.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SetUserRolesRequest(
        @NotNull
        List<UUID> roleIds
) {
}
