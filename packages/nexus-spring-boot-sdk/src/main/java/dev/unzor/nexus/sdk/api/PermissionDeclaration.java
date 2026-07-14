package dev.unzor.nexus.sdk.api;

/**
 * Un permiso declarado por la app ({@code POST /api/v1/permissions/declare}).
 */
public record PermissionDeclaration(String key, String label) {

    public static PermissionDeclaration of(String key) {
        return new PermissionDeclaration(key, null);
    }

    public static PermissionDeclaration of(String key, String label) {
        return new PermissionDeclaration(key, label);
    }
}
