package dev.unzor.nexus.sdk.internal;

import dev.unzor.nexus.sdk.api.AuthorizationSnapshot;
import dev.unzor.nexus.sdk.api.PermissionDeclaration;
import dev.unzor.nexus.sdk.api.PermissionDeclarationReceipt;

import java.util.List;
import java.util.UUID;

/**
 * Cliente crudo de permisos del API de proyecto: snapshot
 * ({@code GET /api/v1/authz/users/{userId}/snapshot}, scope {@code authz:snapshot})
 * y declaración ({@code POST /api/v1/permissions/declare}, scope
 * {@code permissions:declare}). El cacheo + resolución local de comodines vive en
 * {@link dev.unzor.nexus.sdk.PermissionSnapshotCache}.
 */
public class PermissionClient {

    private final NexusHttpClient http;

    public PermissionClient(NexusHttpClient http) {
        this.http = http;
    }

    public AuthorizationSnapshot snapshot(UUID userId) {
        return http.get("/api/v1/authz/users/" + userId + "/snapshot", AuthorizationSnapshot.class);
    }

    public PermissionDeclarationReceipt declare(List<PermissionDeclaration> declarations) {
        // El backend acepta un JSON array suelto ([{key,label},...]), no un wrapper.
        return http.post("/api/v1/permissions/declare", declarations, PermissionDeclarationReceipt.class);
    }
}
