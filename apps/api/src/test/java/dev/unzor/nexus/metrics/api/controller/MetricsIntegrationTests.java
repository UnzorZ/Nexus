package dev.unzor.nexus.metrics.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.modules.domain.entity.ProjectModule;
import dev.unzor.nexus.modules.domain.enums.NexusModule;
import dev.unzor.nexus.modules.persistence.repository.ProjectModuleRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validación end-to-end del módulo metrics: reporte runtime por API key + scope
 * {@code metrics:write}, lectura panel agregada por nombre, validación, scope y
 * gateo por módulo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MetricsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectModuleRepository moduleRepository;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    void runtimeRecordsAndPanelAggregates() throws Exception {
        String ownerEmail = unique("met-owner");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("met"));
        String key = createKey(owner, projectId, "[\"metrics:write\"]");

        recordPoint(key, "cpu.load", 0.42);
        recordPoint(key, "cpu.load", 0.55);
        recordPoint(key, "req.count", 10.0);

        // Panel: series por nombre.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/metrics", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name=='cpu.load')].lastValue").value(0.55))
                .andExpect(jsonPath("$[?(@.name=='cpu.load')].pointCount").value(2))
                .andExpect(jsonPath("$[?(@.name=='cpu.load')].points.length()").value(2))
                .andExpect(jsonPath("$[?(@.name=='req.count')].lastValue").value(10.0));
    }

    @Test
    void invalidRecordBodyIsRejected() throws Exception {
        String ownerEmail = unique("met-valid");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("met"));
        String key = createKey(owner, projectId, "[\"metrics:write\"]");

        mockMvc.perform(post("/api/v1/metrics/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content("{\"name\":\"\",\"value\":1.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void runtimeRequiresScopeAndKey() throws Exception {
        mockMvc.perform(post("/api/v1/metrics/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"value\":1.0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_api_key"));

        String ownerEmail = unique("met-scope");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("met"));
        String wrongScope = createKey(owner, projectId, "[\"other:thing\"]");

        mockMvc.perform(post("/api/v1/metrics/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", wrongScope)
                        .content("{\"name\":\"x\",\"value\":1.0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("missing_scope"));
    }

    @Test
    void disabledModuleRejectsPanelAndRuntime() throws Exception {
        String ownerEmail = unique("met-gate");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("met"));
        String key = createKey(owner, projectId, "[\"metrics:write\"]");

        setModule(projectId, NexusModule.METRICS, false);

        mockMvc.perform(post("/api/v1/metrics/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content("{\"name\":\"x\",\"value\":1.0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/metrics", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));
    }

    private void recordPoint(String key, String name, double value) throws Exception {
        mockMvc.perform(post("/api/v1/metrics/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content("{\"name\":\"" + name + "\",\"value\":" + value + "}"))
                .andExpect(status().isOk());
    }

    // --- helpers -----------------------------------------------------------------

    private void setModule(String projectId, NexusModule module, boolean enabled) {
        UUID pid = UUID.fromString(projectId);
        ProjectModule row = moduleRepository.findByProjectIdAndModule(pid, module)
                .orElseGet(() -> new ProjectModule(pid, module, enabled));
        row.setEnabled(enabled);
        moduleRepository.save(row);
    }

    private String createKey(LoginSession owner, String projectId, String scopesJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects/{projectId}/api-keys", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"name\":\"CI\",\"scopes\":" + scopesJson + ",\"expiresAt\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/secret").asText();
    }

    private String createProject(LoginSession owner, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Metrics Test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/id").asText();
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
        MvcResult loginResult = mockMvc.perform(post("/panel/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .header("User-Agent", "Mozilla/5.0 (Test Runner)")
                        .param("username", email)
                        .param("password", "plain-password"))
                .andExpect(status().is3xxRedirection())
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
