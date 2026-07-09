package io.nexus.client.internal;

import io.nexus.client.NexusProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Cliente HTTP de bajo nivel al API de proyecto de Nexus ({@code /api/v1/**}).
 * Fija la URL base ({@code nexus.url}) y la autenticación: si hay un instance
 * token válido (handshake ADR-1212) envía {@code X-Nexus-Instance-Token}; si no,
 * cae al {@code X-Nexus-Api-Key} crudo. Así los latidos nunca fallan por token
 * caducado: cuando expira, el scheduler lo re-registra y, hasta entonces, se usa
 * la API key.
 *
 * <p>El {@code projectId} nunca lo envía el cliente: lo resuelve Nexus a partir
 * de la credencial.</p>
 */
public class NexusHttpClient {

    private final String baseUrl;
    private final String apiKey;
    private final String appName;
    private volatile InstanceCred instanceToken;
    private final RestClient restClient;

    public NexusHttpClient(NexusProperties properties) {
        this.baseUrl = normalizeBase(properties.getUrl());
        this.apiKey = properties.getApiKey();
        this.appName = properties.getAppName();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(new AuthInterceptor(this))
                .build();
    }

    /** Registra un instance token efímero para latidos (expira a {@code expiresAt}). */
    public void useInstanceToken(String token, Instant expiresAt) {
        this.instanceToken = (token == null || token.isBlank()) ? null : new InstanceCred(token, expiresAt);
    }

    /** Fuerza el uso de la API key cruda (p. ej. antes de /register, que la exige). */
    public void clearInstanceToken() {
        this.instanceToken = null;
    }

    public boolean instanceTokenValid() {
        InstanceCred t = instanceToken;
        return t != null && t.expiresAt.isAfter(Instant.now());
    }

    public RestClient rest() {
        return restClient;
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body)
                .retrieve()
                .body(responseType);
    }

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

    private record InstanceCred(String token, Instant expiresAt) {}

    /** Añade la cabecera de auth adecuada a cada petición (instance token válido o API key). */
    private static final class AuthInterceptor implements ClientHttpRequestInterceptor {
        private final NexusHttpClient owner;

        AuthInterceptor(NexusHttpClient owner) {
            this.owner = owner;
        }

        @Override
        public ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            InstanceCred t = owner.instanceToken;
            if (t != null && t.expiresAt.isAfter(Instant.now())) {
                request.getHeaders().set("X-Nexus-Instance-Token", t.token);
            } else {
                request.getHeaders().set("X-Nexus-Api-Key", owner.apiKey);
            }
            // X-Nexus-App identifica al emisor para la sincronización declarativa
            // (declare app-scoped); el backend lo ignora en el resto de endpoints.
            if (owner.appName != null && !owner.appName.isBlank()) {
                request.getHeaders().set("X-Nexus-App", owner.appName);
            }
            return execution.execute(request, body);
        }
    }

    private static String normalizeBase(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("nexus.url must be configured");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
