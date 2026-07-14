package dev.unzor.nexus.sdk.internal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.unzor.nexus.sdk.NexusProperties;
import dev.unzor.nexus.sdk.api.ConfigValue;
import dev.unzor.nexus.sdk.api.MetricPoint;
import dev.unzor.nexus.sdk.api.VaultSecret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que los clientes config/metrics/vault llaman a los endpoints y cuerpos
 * correctos, contra un stub HTTP del JDK (sin contexto Spring, sin red externa).
 */
class SdkModulesClientTest {

    private HttpServer server;
    private CapturingHandler handler;
    private ConfigClient configClient;
    private VaultClient vaultClient;
    private MetricsClient metricsClient;

    @BeforeEach
    void setUp() throws IOException {
        handler = new CapturingHandler();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", handler);
        server.start();
        NexusProperties props = new NexusProperties();
        props.setUrl("http://localhost:" + server.getAddress().getPort());
        props.setApiKey("test-key");
        NexusHttpClient http = new NexusHttpClient(props);
        configClient = new ConfigClient(http);
        vaultClient = new VaultClient(http);
        metricsClient = new MetricsClient(http);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void configListAndGetHitRightPaths() {
        List<ConfigValue> values = configClient.list();
        assertThat(handler.method).isEqualTo("GET");
        assertThat(handler.path).isEqualTo("/api/v1/config/values");
        assertThat(values).hasSize(1);

        configClient.get("feature.flag");
        assertThat(handler.path).isEqualTo("/api/v1/config/values/feature.flag");
    }

    @Test
    void vaultListAndGetHitRightPaths() {
        vaultClient.list();
        assertThat(handler.method).isEqualTo("GET");
        assertThat(handler.path).isEqualTo("/api/v1/vault/secrets");

        VaultSecret secret = vaultClient.get("stripe-key");
        assertThat(handler.path).isEqualTo("/api/v1/vault/secrets/stripe-key");
        assertThat(secret.value()).isEqualTo("sk_test_123");
    }

    @Test
    void metricsRecordPostsRightBody() {
        MetricPoint point = metricsClient.record("orders.created", 42.0, Map.of("env", "prod"));
        assertThat(handler.method).isEqualTo("POST");
        assertThat(handler.path).isEqualTo("/api/v1/metrics/record");
        assertThat(handler.body).contains("\"name\":\"orders.created\"");
        assertThat(handler.body).contains("\"value\":42.0");
        assertThat(handler.body).contains("\"env\":\"prod\"");
        assertThat(point.value()).isEqualTo(42.0);
    }

    /** Captura method/path/body y devuelve JSON canned según el path. */
    static class CapturingHandler implements HttpHandler {
        String method;
        String path;
        String body;

        @Override
        public void handle(HttpExchange ex) throws IOException {
            method = ex.getRequestMethod();
            path = ex.getRequestURI().getPath();
            body = new String(ex.getRequestBody().readAllBytes());
            byte[] bytes = responseFor(path).getBytes();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.getResponseBody().close();
        }

        private String responseFor(String p) {
            String now = "\"2026-07-10T10:00:00Z\"";
            String uuid = "\"11111111-1111-1111-1111-111111111111\"";
            if (p.endsWith("/config/values")) {
                return "[{\"id\":" + uuid + ",\"key\":\"feature.flag\",\"value\":\"on\",\"valueType\":\"STRING\","
                        + "\"createdAt\":" + now + ",\"updatedAt\":" + now + "}]";
            }
            if (p.matches(".*/config/values/[^/]+$")) {
                return "{\"id\":" + uuid + ",\"key\":\"feature.flag\",\"value\":\"on\",\"valueType\":\"STRING\","
                        + "\"createdAt\":" + now + ",\"updatedAt\":" + now + "}";
            }
            if (p.endsWith("/vault/secrets")) {
                return "[{\"id\":" + uuid + ",\"key\":\"stripe-key\",\"cipher\":\"AES_256_GCM\","
                        + "\"createdAt\":" + now + ",\"updatedAt\":" + now + ",\"lastRotatedAt\":" + now + "}]";
            }
            if (p.matches(".*/vault/secrets/[^/]+$")) {
                return "{\"key\":\"stripe-key\",\"value\":\"sk_test_123\"}";
            }
            if (p.endsWith("/metrics/record")) {
                return "{\"value\":42.0,\"tags\":{\"env\":\"prod\"},\"recordedAt\":" + now + "}";
            }
            return "{}";
        }
    }
}
