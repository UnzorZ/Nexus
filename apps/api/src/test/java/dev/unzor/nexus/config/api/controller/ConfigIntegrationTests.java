package dev.unzor.nexus.config.api.controller;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validación end-to-end del módulo config: panel CRUD tipado, lectura runtime por
 * API key + scope {@code config:read}, validación de tipos, control de acceso y
 * gateo por módulo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConfigIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectModuleRepository moduleRepository;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    void panelCrudAndRuntimeRead() throws Exception {
        String ownerEmail = unique("cfg-owner");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("cfg"));
        String key = createKey(owner, projectId, "[\"config:read\"]");

        // Panel: lista vacía.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/config", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Upsert create.
        mockMvc.perform(put("/api/panel/v1/projects/{projectId}/config/{key}", projectId, "feature.beta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"value\":\"true\",\"valueType\":\"BOOLEAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("feature.beta"))
                .andExpect(jsonPath("$.value").value("true"))
                .andExpect(jsonPath("$.valueType").value("BOOLEAN"));

        // Upsert update (misma clave).
        mockMvc.perform(put("/api/panel/v1/projects/{projectId}/config/{key}", projectId, "feature.beta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"value\":\"false\",\"valueType\":\"BOOLEAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("false"));

        // Runtime: lectura por API key.
        mockMvc.perform(get("/api/v1/config/values").header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("feature.beta"))
                .andExpect(jsonPath("$[0].value").value("false"));

        mockMvc.perform(get("/api/v1/config/values/{key}", "feature.beta").header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("false"));

        mockMvc.perform(get("/api/v1/config/values/{key}", "missing").header("X-Nexus-Api-Key", key))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));

        // Delete.
        mockMvc.perform(delete("/api/panel/v1/projects/{projectId}/config/{key}", projectId, "feature.beta")
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/config", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidValueForTypeIsRejected() throws Exception {
        String ownerEmail = unique("cfg-valid");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("cfg"));

        mockMvc.perform(put("/api/panel/v1/projects/{projectId}/config/{key}", projectId, "n")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"value\":\"not-a-number\",\"valueType\":\"NUMBER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void runtimeRequiresScopeAndKey() throws Exception {
        mockMvc.perform(get("/api/v1/config/values"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_api_key"));

        String ownerEmail = unique("cfg-scope");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("cfg"));
        String wrongScope = createKey(owner, projectId, "[\"other:thing\"]");

        mockMvc.perform(get("/api/v1/config/values").header("X-Nexus-Api-Key", wrongScope))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("missing_scope"));
    }

    @Test
    void disabledModuleRejectsPanelAndRuntime() throws Exception {
        String ownerEmail = unique("cfg-gate");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("cfg"));
        String key = createKey(owner, projectId, "[\"config:read\"]");

        setModule(projectId, NexusModule.CONFIG, false);

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/config", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));

        mockMvc.perform(get("/api/v1/config/values").header("X-Nexus-Api-Key", key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));
    }

    @Test
    void nonMemberCannotReadConfig() throws Exception {
        String ownerEmail = unique("cfg-mem-owner");
        String outsiderEmail = unique("cfg-mem-out");
        registerAccount(ownerEmail);
        registerAccount(outsiderEmail);
        LoginSession owner = login(ownerEmail);
        LoginSession outsider = login(outsiderEmail);
        String projectId = createProject(owner, randomSlug("cfg"));

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/config", projectId)
                        .cookie(outsider.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("permission_denied"));

        // El propietario sí.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/config", projectId)
                        .cookie(owner.sessionCookie()))
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
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Config Test\"}"))
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
