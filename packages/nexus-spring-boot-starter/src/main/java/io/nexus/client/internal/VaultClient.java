package io.nexus.client.internal;

import io.nexus.client.api.VaultSecret;
import io.nexus.client.api.VaultSecretSummary;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;

/**
 * Cliente crudo del vault del API de proyecto: listado de metadatos y lectura
 * del valor desencriptado ({@code GET /api/v1/vault/secrets[/{key}]}, scope
 * {@code vault:read}). El listado no expone valores; {@link #get(String)} sí.
 */
public class VaultClient {

    private final NexusHttpClient http;

    public VaultClient(NexusHttpClient http) {
        this.http = http;
    }

    public List<VaultSecretSummary> list() {
        return http.rest().get().uri("/api/v1/vault/secrets")
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public VaultSecret get(String key) {
        return http.rest().get().uri("/api/v1/vault/secrets/{key}", key)
                .retrieve().body(VaultSecret.class);
    }
}
