package io.nexus.client.internal;

import io.nexus.client.api.ConfigValue;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;

/**
 * Cliente crudo de configuración del API de proyecto: lectura de valores
 * ({@code GET /api/v1/config/values[/{key}]}, scope {@code config:read}). El
 * {@code value} se devuelve en claro (no es un secreto — para secretos usa
 * {@link VaultClient}).
 */
public class ConfigClient {

    private final NexusHttpClient http;

    public ConfigClient(NexusHttpClient http) {
        this.http = http;
    }

    public List<ConfigValue> list() {
        return http.rest().get().uri("/api/v1/config/values")
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public ConfigValue get(String key) {
        return http.rest().get().uri("/api/v1/config/values/{key}", key)
                .retrieve().body(ConfigValue.class);
    }
}
