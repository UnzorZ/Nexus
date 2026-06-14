package dev.unzor.nexus.admin.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the fail-closed behavior when Redis is unreachable (scenarios 8–9 of the
 * shared-Redis spec): protected endpoints answer 503 redis_unavailable when they need
 * Redis, and there is never anonymous access to a protected endpoint.
 *
 * <p>Uses a real embedded server ({@code webEnvironment = RANDOM_PORT}) so the servlet
 * filter chain — including {@code RedisUnavailableFilter} — runs exactly as in
 * production. A dedicated Redis container (registered via {@link DynamicPropertySource}
 * so it overrides the shared one) can be stopped mid-test. {@link TestcontainersConfiguration}
 * still supplies PostgreSQL.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedisUnavailableTests {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.8.0-alpine"))
            .withExposedPorts(6379);

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:latest"));

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.url",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Test
    void redisDownReturnsServiceUnavailableAndNeverAllowsAnonymousAccess() {
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        // Register an account so we can perform a real login and obtain a genuine
        // JSESSIONID cookie. That cookie is needed to force Spring Session to consult
        // Redis (load) on the next request.
        String email = "redis-down-" + java.util.UUID.randomUUID() + "@example.com";
        HttpHeaders csrfHeaders = new HttpHeaders();
        csrfHeaders.setAccept(List.of(MediaType.ALL));
        ResponseEntity<Void> csrf = client.method(HttpMethod.GET).uri("/api/panel/v1/csrf")
                .retrieve().onStatus(s -> true, (req, res) -> { }).toBodilessEntity();
        String xsrf = extractCookie(csrf, "XSRF-TOKEN");

        client.method(HttpMethod.POST).uri("/api/panel/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-XSRF-TOKEN", xsrf)
                .header(HttpHeaders.COOKIE, cookieHeader(csrf, "XSRF-TOKEN"))
                .body("{\"email\":\"" + email + "\",\"password\":\"plain-password\",\"displayName\":\"Owner\"}")
                .retrieve().onStatus(s -> true, (req, res) -> { }).toBodilessEntity();

        ResponseEntity<Void> login = client.method(HttpMethod.POST).uri("/panel/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-XSRF-TOKEN", xsrf)
                .header(HttpHeaders.COOKIE, cookieHeader(csrf, "XSRF-TOKEN"))
                .body("username=" + email + "&password=plain-password")
                .retrieve().onStatus(s -> true, (req, res) -> { }).toBodilessEntity();
        String jsessionId = extractCookie(login, "JSESSIONID");
        assertThat(jsessionId).as("JSESSIONID issued by login").isNotNull();

        // Before stopping Redis, a protected endpoint without session is 401 (not 200).
        ResponseEntity<Void> before = client.method(HttpMethod.GET).uri("/api/panel/v1/me")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().onStatus(s -> true, (req, res) -> { }).toBodilessEntity();
        assertThat(before.getStatusCode().value()).isEqualTo(401);

        REDIS.stop();

        // Without a session cookie, Redis is not consulted on load, so the protected
        // endpoint stays 401 — never 200/anonymous.
        ResponseEntity<Void> withoutCookie = client.method(HttpMethod.GET).uri("/api/panel/v1/sessions")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().onStatus(s -> true, (req, res) -> { }).toBodilessEntity();
        assertThat(withoutCookie.getStatusCode().value()).isEqualTo(401);

        // The genuine session cookie forces Spring Session to load the session from Redis;
        // with Redis down this must surface as 503 redis_unavailable.
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> withCookie = client.method(HttpMethod.GET).uri("/api/panel/v1/me")
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.COOKIE, "JSESSIONID=" + jsessionId)
                .retrieve().onStatus(s -> true, (req, res) -> { }).toEntity(Map.class);
        assertThat(withCookie.getStatusCode().value()).isEqualTo(503);
        assertThat(withCookie.getBody()).isNotNull();
        assertThat(withCookie.getBody().get("code")).isEqualTo("redis_unavailable");
    }

    private static String extractCookie(ResponseEntity<?> response, String name) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith(name + "="))
                .map(c -> c.substring(name.length() + 1, c.indexOf(';', name.length())))
                .findFirst()
                .orElse(null);
    }

    private static String cookieHeader(ResponseEntity<?> response, String name) {
        return name + "=" + extractCookie(response, name);
    }
}
