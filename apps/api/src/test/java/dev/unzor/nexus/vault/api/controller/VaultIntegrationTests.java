package dev.unzor.nexus.vault.api.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validación end-to-end del módulo vault: escritura/rotación/borrado (panel, sin
 * exponer el valor), lectura descifrada runtime (round-trip AES-GCM), conflictos,
 * validación, scope y gateo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VaultIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectModuleRepository moduleRepository;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    void writeRotatesAndRuntimeReadsBackDecrypted() throws Exception {
        String ownerEmail = unique("vlt-owner");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("vlt"));
        String key = createKey(owner, projectId, "[\"vault:read\"]");

        // Create: el panel no devuelve el valor.
        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/vault/secrets/{key}", projectId, "db.password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"value\":\"s3cret\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("db.password"))
                .andExpect(jsonPath("$.value").doesNotExist());

        // Panel list: sólo metadatos.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/vault/secrets", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("db.password"))
                .andExpect(jsonPath("$[0].value").doesNotExist());

        // Runtime read: round-trip (cifrado → descifrado devuelve el valor original).
        mockMvc.perform(get("/api/v1/vault/secrets/{key}", "db.password").header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("db.password"))
                .andExpect(jsonPath("$.value").value("s3cret"));

        // Rotate: el nuevo valor se lee tras la rotación.
        mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/vault/secrets/{key}", projectId, "db.password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"value\":\"r0tated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastRotatedAt").exists());

        mockMvc.perform(get("/api/v1/vault/secrets/{key}", "db.password").header("X-Nexus-Api-Key", key))
                .andExpect(jsonPath("$.value").value("r0tated"));

        // Missing key → 404.
        mockMvc.perform(get("/api/v1/vault/secrets/{key}", "missing").header("X-Nexus-Api-Key", key))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void duplicateKeyConflictsAndValidationRejectsBlank() throws Exception {
        String ownerEmail = unique("vlt-conf");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("vlt"));

        writeSecret(owner, projectId, "k", "v1");

        // Duplicate → 409.
        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/vault/secrets/{key}", projectId, "k")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"value\":\"v2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        // Blank value → 400.
        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/vault/secrets/{key}", projectId, "blank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void runtimeRequiresScopeAndKey() throws Exception {
        mockMvc.perform(get("/api/v1/vault/secrets/k"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_api_key"));

        String ownerEmail = unique("vlt-scope");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("vlt"));
        String wrongScope = createKey(owner, projectId, "[\"other:thing\"]");

        mockMvc.perform(get("/api/v1/vault/secrets/k").header("X-Nexus-Api-Key", wrongScope))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("missing_scope"));
    }

    @Test
    void disabledModuleRejectsPanelAndRuntime() throws Exception {
        String ownerEmail = unique("vlt-gate");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("vlt"));
        String key = createKey(owner, projectId, "[\"vault:read\"]");

        setModule(projectId, NexusModule.VAULT, false);

        mockMvc.perform(get("/api/v1/vault/secrets").header("X-Nexus-Api-Key", key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/vault/secrets", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));
    }

    private void writeSecret(LoginSession owner, String projectId, String key, String value) throws Exception {
        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/vault/secrets/{key}", projectId, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"value\":\"" + value + "\"}"))
                .andExpect(status().isCreated());
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
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Vault Test\"}"))
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
