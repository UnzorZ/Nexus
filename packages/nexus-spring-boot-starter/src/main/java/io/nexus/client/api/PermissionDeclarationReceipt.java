package io.nexus.client.api;

/** Respuesta de {@code POST /api/v1/permissions/declare}. */
public record PermissionDeclarationReceipt(int declared, int created, int markedMissing) {
}
