package io.nexus.client.internal;

import io.nexus.client.NexusProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Cliente HTTP de bajo nivel al API de proyecto de Nexus ({@code /api/v1/**}).
 * Fija la URL base ({@code nexus.url}) y la cabecera {@code X-Nexus-Api-Key}.
 *
 * <p>El {@code projectId} nunca lo envía el cliente: lo resuelve Nexus a partir
 * de la API key. El instance token (handshake ADR-0012) se puede fijar con
 * {@link #useInstanceToken(String)} para latidos de alta frecuencia.</p>
 */
public class NexusHttpClient {

    private final String baseUrl;
    private volatile RestClient restClient;

    public NexusHttpClient(NexusProperties properties) {
        this.baseUrl = normalizeBase(properties.getUrl());
        this.restClient = builder(properties.getApiKey(), null).baseUrl(baseUrl).build();
    }

    /** Cambia la autenticación de la API key cruda a un instance token efímero. */
    public void useInstanceToken(String instanceToken) {
        this.restClient = builder(null, instanceToken).baseUrl(baseUrl).build();
    }

    public RestClient rest() {
        return restClient;
    }

    /** POST JSON con cuerpo cualquiera; devuelve el tipo indicado. */
    public <T> T post(String path, Object body, Class<T> responseType) {
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body)
                .retrieve()
                .body(responseType);
    }

    /** POST form-urlencoded (p. ej. notify/send o logout_token). */
    public void postForm(String path, String formBody) {
        restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .toBodilessEntity();
    }

    public <T> T get(String path, Class<T> responseType) {
        return restClient.get().uri(path).retrieve().body(responseType);
    }

    private static RestClient.Builder builder(String apiKey, String instanceToken) {
        RestClient.Builder b = RestClient.builder();
        if (apiKey != null && !apiKey.isBlank()) {
            b.defaultHeader("X-Nexus-Api-Key", apiKey);
        }
        if (instanceToken != null && !instanceToken.isBlank()) {
            b.defaultHeader("X-Nexus-Instance-Token", instanceToken);
        }
        return b;
    }

    private static String normalizeBase(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("nexus.url must be configured");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
