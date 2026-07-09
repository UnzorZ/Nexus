package dev.unzor.nexus.permissions.api.dto;

/**
 * Resultado de una sincronización declarativa de permisos (spec §18 SDK).
 *
 * @param declared     permisos declarados en este ciclo (tamaño del lote válido).
 * @param created      permisos nuevos creados (no existían en el catálogo).
 * @param markedMissing permisos de origen CODE/YAML marcados ausentes (la app
 *                     dejó de declararlos este ciclo).
 */
public record PermissionDeclarationReceipt(int declared, int created, int markedMissing) {
}
