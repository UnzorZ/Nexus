package dev.unzor.nexus.notify.api.controller;

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
 * Validación end-to-end del módulo notify: CRUD de plantillas, envío runtime con
 * render de plantilla, historial, validación, scope y gateo. Sin SMTP en el
 * entorno de test, los envíos quedan FAILED (lo que ejercita el flujo de
 * registro + state machine).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotifyIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectModuleRepository moduleRepository;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    void templateSendRenderAndHistory() throws Exception {
        String ownerEmail = unique("ntf-owner");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("ntf"));
        String key = createKey(owner, projectId, "[\"notify:send\"]");

        // Create template.
        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/notify/templates", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"name\":\"welcome\",\"subject\":\"Hi {{name}}\","
                                + "\"bodyTemplate\":\"Welcome {{name}}!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("welcome"));

        // Runtime send via template: rendered subject/body; delivery FAILED (no SMTP).
        mockMvc.perform(post("/api/v1/notify/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content("{\"to\":\"user@example.com\",\"templateName\":\"welcome\","
                                + "\"variables\":{\"name\":\"Sam\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("Hi Sam"))
                .andExpect(jsonPath("$.status").value("FAILED"));

        // Panel history reflects the attempt.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/notify/notifications", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipient").value("user@example.com"))
                .andExpect(jsonPath("$[0].status").value("FAILED"));
    }

    @Test
    void sendRequiresTemplateOrSubjectBody() throws Exception {
        String ownerEmail = unique("ntf-valid");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("ntf"));
        String key = createKey(owner, projectId, "[\"notify:send\"]");

        mockMvc.perform(post("/api/v1/notify/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content("{\"to\":\"user@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void runtimeRequiresScopeAndKey() throws Exception {
        mockMvc.perform(post("/api/v1/notify/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"user@example.com\",\"subject\":\"x\",\"body\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_api_key"));

        String ownerEmail = unique("ntf-scope");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("ntf"));
        String wrongScope = createKey(owner, projectId, "[\"other:thing\"]");

        mockMvc.perform(post("/api/v1/notify/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", wrongScope)
                        .content("{\"to\":\"user@example.com\",\"subject\":\"x\",\"body\":\"y\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("missing_scope"));
    }

    @Test
    void disabledModuleRejectsPanelAndRuntime() throws Exception {
        String ownerEmail = unique("ntf-gate");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("ntf"));
        String key = createKey(owner, projectId, "[\"notify:send\"]");

        setModule(projectId, NexusModule.NOTIFY, false);

        mockMvc.perform(post("/api/v1/notify/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content("{\"to\":\"user@example.com\",\"subject\":\"x\",\"body\":\"y\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/notify/templates", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));
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
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Notify Test\"}"))
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
