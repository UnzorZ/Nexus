package dev.unzor.nexus.registry.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validación runtime del latido ({@code POST /api/v1/registry/heartbeat}) por API
 * key + scope {@code registry:heartbeat}, y exposición en el panel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegistryHeartbeatRuntimeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    void heartbeatRejectsMissingKey() throws Exception {
        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_api_key"));
    }

    @Test
    void heartbeatRejectsMissingScope() throws Exception {
        String ownerEmail = unique("hb-scope-no");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("hb"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"other:thing\"],\"expiresAt\":null}");

        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("missing_scope"));
    }

    @Test
    void heartbeatRejectsInvalidBody() throws Exception {
        String ownerEmail = unique("hb-invalid");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("hb"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content("{\"instanceId\":\"\",\"appName\":\"demo-api\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void heartbeatRecordsInstanceAndExposesItOnPanel() throws Exception {
        String ownerEmail = unique("hb-ok");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("hb"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.nextHeartbeatInSeconds").value(30))
                .andExpect(jsonPath("$.receivedAt").exists());

        // The instance shows up on the panel list, derived ONLINE (just reported).
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/heartbeats", projectId)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instanceId").value("demo-api-01"))
                .andExpect(jsonPath("$[0].liveness").value("ONLINE"));
    }

    @Test
    void registerMintsInstanceToken() throws Exception {
        String ownerEmail = unique("reg-ok");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("reg"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        mockMvc.perform(post("/api/v1/registry/register").header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.projectId").value(projectId));
    }

    @Test
    void heartbeatAcceptsInstanceToken() throws Exception {
        String ownerEmail = unique("tok-ok");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("tok"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        MvcResult reg = mockMvc.perform(post("/api/v1/registry/register").header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).at("/token").asText();

        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Instance-Token", token)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.nextHeartbeatInSeconds").value(30));
    }

    @Test
    void heartbeatRejectsInvalidInstanceToken() throws Exception {
        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Instance-Token", "bogus-token")
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_instance_token"));
    }

    @Test
    void registerRequiresScope() throws Exception {
        String ownerEmail = unique("reg-scope");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("reg"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"other:thing\"],\"expiresAt\":null}");

        mockMvc.perform(post("/api/v1/registry/register").header("X-Nexus-Api-Key", key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("missing_scope"));
    }

    @Test
    void registerRejectsInstanceToken() throws Exception {
        String ownerEmail = unique("reg-tok");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("reg"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        // Bootstrap con la key cruda → token.
        MvcResult reg = mockMvc.perform(post("/api/v1/registry/register").header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).at("/token").asText();

        // Renovar a partir del token (sin la key) se rechaza — no se puede perpetuar un token.
        mockMvc.perform(post("/api/v1/registry/register").header("X-Nexus-Instance-Token", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("raw_api_key_required"));
    }

    @Test
    void heartbeatRejectsTokenAfterKeyDisabled() throws Exception {
        String ownerEmail = unique("tok-rev");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("tok"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        MvcResult reg = mockMvc.perform(post("/api/v1/registry/register").header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).at("/token").asText();

        // El token funciona antes de tocar la key.
        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Instance-Token", token)
                        .content(validBody()))
                .andExpect(status().isOk());

        // Deshabilitar la key por panel revoca sus tokens (P2).
        String keyId = firstKeyId(owner, projectId);
        disableKey(owner, projectId, keyId);

        // El mismo token ya no vale → 401 invalid_instance_token (revocación inmediata).
        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Instance-Token", token)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_instance_token"));
    }

    // --- helpers -----------------------------------------------------------------

    private static String validBody() {
        return "{\"instanceId\":\"demo-api-01\",\"appName\":\"demo-api\",\"appVersion\":\"1.0.0\","
                + "\"status\":\"up\",\"metadata\":{\"javaVersion\":\"21\"}}";
    }

    private String createKey(LoginSession owner, String projectId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects/{projectId}/api-keys", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/secret").asText();
    }

    private String firstKeyId(LoginSession owner, String projectId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/panel/v1/projects/{projectId}/api-keys", projectId)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/0/id").asText();
    }

    private void disableKey(LoginSession owner, String projectId, String keyId) throws Exception {
        mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/api-keys/{keyId}", projectId, keyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"name\":\"CI\",\"status\":\"DISABLED\",\"expiresAt\":null}"))
                .andExpect(status().isOk());
    }

    private String createProject(LoginSession owner, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Heartbeat Test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"id\":\"") + 6;
        return body.substring(start, body.indexOf('"', start));
    }

    private void registerAccount(String email) throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();
        mockMvc.perform(post("/api/panel/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie)
                        .content("{\"email\":\"" + email + "\",\"password\":\"plain-password\",\"displayName\":\"Tester\"}"))
                .andExpect(status().isCreated());
    }

    private LoginSession login(String email) throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();
        MvcResult loginResult = mockMvc.perform(post("/api/panel/v1/session/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .header("User-Agent", "Mozilla/5.0 (Test Runner)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\""+email+"\",\"password\":\"plain-password\"}"))
                        .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = cookieByName(loginResult, "JSESSIONID");
        if (sessionCookie == null) {
            throw new IllegalStateException("JSESSIONID not issued after login");
        }
        return new LoginSession(sessionCookie, csrfCookie.getValue(), csrfCookie);
    }

    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            throw new IllegalStateException("XSRF-TOKEN cookie was not issued");
        }
        return cookie;
    }

    private static Cookie cookieByName(MvcResult result, String name) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static String randomSlug(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record LoginSession(Cookie sessionCookie, String csrfToken, Cookie csrfCookie) {
    }
}
