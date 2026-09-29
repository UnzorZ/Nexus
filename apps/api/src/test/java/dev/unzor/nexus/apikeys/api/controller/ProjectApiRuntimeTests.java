package dev.unzor.nexus.apikeys.api.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.apikeys.persistence.repository.ProjectApiKeyRepository;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validación runtime del API de proyecto ({@code /api/v1/**}) por API key.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestScopedController.class})
class ProjectApiRuntimeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectApiKeyRepository apiKeyRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void whoamiRejectsMissingKey() throws Exception {
        mockMvc.perform(get("/api/v1/whoami"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_api_key"));
    }

    @Test
    void whoamiAcceptsAValidKey() throws Exception {
        String ownerEmail = unique("rt-owner");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("rt"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        mockMvc.perform(get("/api/v1/whoami").header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.scopes[0]").value("registry:heartbeat"));
    }

    @Test
    void whoamiRejectsKeyForArchivedProjectWithoutTouchingLastUsed() throws Exception {
        String ownerEmail = unique("rt-archived");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("rt"));
        CreatedKey created = createKeyWithId(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        archiveProject(owner, projectId);

        mockMvc.perform(get("/api/v1/whoami").header("X-Nexus-Api-Key", created.key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("project_not_operational"));
        assertLastUsedIsNull(projectId, created.id);
    }

    @Test
    void whoamiRejectsKeyForSuspendedProjectWithoutTouchingLastUsed() throws Exception {
        String ownerEmail = unique("rt-suspended");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("rt"));
        CreatedKey created = createKeyWithId(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");
        Project project = projectRepository.findById(UUID.fromString(projectId)).orElseThrow();
        project.suspend();
        projectRepository.saveAndFlush(project);

        mockMvc.perform(get("/api/v1/whoami").header("X-Nexus-Api-Key", created.key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("project_not_operational"));
        assertLastUsedIsNull(projectId, created.id);
    }

    @Test
    void whoamiRejectsDisabledKey() throws Exception {
        String ownerEmail = unique("rt-disabled");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("rt"));
        CreatedKey created = createKeyWithId(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        disableKey(owner, projectId, created.id);

        mockMvc.perform(get("/api/v1/whoami").header("X-Nexus-Api-Key", created.key))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("api_key_disabled"));
    }

    @Test
    void whoamiRejectsExpiredKey() throws Exception {
        String ownerEmail = unique("rt-expired");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("rt"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":\"2020-01-01T00:00:00Z\"}");

        mockMvc.perform(get("/api/v1/whoami").header("X-Nexus-Api-Key", key))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("api_key_expired"));
    }

    @Test
    void whoamiRejectsInvalidKey() throws Exception {
        mockMvc.perform(get("/api/v1/whoami").header("X-Nexus-Api-Key", "nxs_bogus_zzzzzzzzzzzz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_api_key"));
    }

    @Test
    void scopedEndpointRejectsMissingScope() throws Exception {
        String ownerEmail = unique("rt-scope-no");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("rt"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"other:thing\"],\"expiresAt\":null}");

        mockMvc.perform(get("/api/v1/test/scoped").header("X-Nexus-Api-Key", key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("missing_scope"));
    }

    @Test
    void scopedEndpointAcceptsMatchingScope() throws Exception {
        String ownerEmail = unique("rt-scope-ok");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("rt"));
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"test:scoped\"],\"expiresAt\":null}");

        mockMvc.perform(get("/api/v1/test/scoped").header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void unannotatedEndpointIsDeniedByDefaultForAnyValidKey() throws Exception {
        String ownerEmail = unique("rt-deny");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("rt"));
        // Deny-by-default (M5): un endpoint /api/v1/** sin @RequiredScope ni
        // @ScopeFree se deniega para una key autenticada válida — depende del
        // endpoint, no de los scopes de la key.
        String key = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"other:thing\"],\"expiresAt\":null}");

        mockMvc.perform(get("/api/v1/test/unscoped").header("X-Nexus-Api-Key", key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("scope_required"));
    }

    // --- editar scopes por panel ------------------------------------------------
    // Modos de fallo: (1) el PATCH ignora los scopes y la key no gana el nuevo;
    // (2) quitar un scope no corta el acceso al momento; (3) un PATCH sin
    // "scopes" (clientes antiguos) borra los scopes de la key; (4) un scope con
    // formato inválido o nulo se guarda o da 500; (5) un miembro sin permiso de
    // gestión cambia los scopes.

    @Test
    void patchScopesGrantsTheNewScopeAtOnce() throws Exception {
        LoginSession owner = newOwner("rt-edit-add");
        String projectId = createProject(owner, randomSlug("rt"));
        CreatedKey created = createKeyWithId(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"other:thing\"],\"expiresAt\":null}");

        patchKey(owner, projectId, created.id,
                "{\"name\":\"CI\",\"status\":\"ACTIVE\",\"expiresAt\":null,\"scopes\":[\"other:thing\",\"test:scoped\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes.length()").value(2))
                .andExpect(jsonPath("$.scopes[1]").value("test:scoped"));

        mockMvc.perform(get("/api/v1/test/scoped").header("X-Nexus-Api-Key", created.key))
                .andExpect(status().isOk());
    }

    @Test
    void patchScopesRevokesARemovedScopeAtOnce() throws Exception {
        LoginSession owner = newOwner("rt-edit-rm");
        String projectId = createProject(owner, randomSlug("rt"));
        CreatedKey created = createKeyWithId(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"test:scoped\"],\"expiresAt\":null}");
        mockMvc.perform(get("/api/v1/test/scoped").header("X-Nexus-Api-Key", created.key))
                .andExpect(status().isOk());

        patchKey(owner, projectId, created.id,
                "{\"name\":\"CI\",\"status\":\"ACTIVE\",\"expiresAt\":null,\"scopes\":[\"other:thing\"]}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/test/scoped").header("X-Nexus-Api-Key", created.key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("missing_scope"));
    }

    @Test
    void patchWithoutScopesKeepsTheCurrentScopes() throws Exception {
        LoginSession owner = newOwner("rt-edit-keep");
        String projectId = createProject(owner, randomSlug("rt"));
        CreatedKey created = createKeyWithId(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"test:scoped\"],\"expiresAt\":null}");

        patchKey(owner, projectId, created.id, "{\"name\":\"Renamed\",\"status\":\"ACTIVE\",\"expiresAt\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[0]").value("test:scoped"));

        mockMvc.perform(get("/api/v1/test/scoped").header("X-Nexus-Api-Key", created.key))
                .andExpect(status().isOk());
    }

    @Test
    void patchRejectsAnInvalidScopeAndKeepsTheOldOnes() throws Exception {
        LoginSession owner = newOwner("rt-edit-bad");
        String projectId = createProject(owner, randomSlug("rt"));
        CreatedKey created = createKeyWithId(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"test:scoped\"],\"expiresAt\":null}");

        patchKey(owner, projectId, created.id,
                "{\"name\":\"CI\",\"status\":\"ACTIVE\",\"expiresAt\":null,\"scopes\":[\"NOT A SCOPE\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
        patchKey(owner, projectId, created.id,
                "{\"name\":\"CI\",\"status\":\"ACTIVE\",\"expiresAt\":null,\"scopes\":[null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));

        mockMvc.perform(get("/api/v1/test/scoped").header("X-Nexus-Api-Key", created.key))
                .andExpect(status().isOk());
    }

    // --- helpers -----------------------------------------------------------------

    private record CreatedKey(String id, String key) {
    }

    private String createKey(LoginSession owner, String projectId, String body) throws Exception {
        return createKeyWithId(owner, projectId, body).key;
    }

    private CreatedKey createKeyWithId(LoginSession owner, String projectId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects/{projectId}/api-keys", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CreatedKey(root.at("/id").asText(), root.at("/secret").asText());
    }

    private LoginSession newOwner(String prefix) throws Exception {
        String email = unique(prefix);
        registerAccount(email);
        return login(email);
    }

    private org.springframework.test.web.servlet.ResultActions patchKey(
            LoginSession owner, String projectId, String keyId, String body) throws Exception {
        return mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/api-keys/{keyId}", projectId, keyId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-XSRF-TOKEN", owner.csrfToken())
                .cookie(owner.csrfCookie(), owner.sessionCookie())
                .content(body));
    }

    private void disableKey(LoginSession owner, String projectId, String keyId) throws Exception {
        mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/api-keys/{keyId}", projectId, keyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"name\":\"CI\",\"status\":\"DISABLED\",\"expiresAt\":null}"))
                .andExpect(status().isOk());
    }

    private void archiveProject(LoginSession owner, String projectId) throws Exception {
        mockMvc.perform(delete("/api/panel/v1/projects/{projectId}", projectId)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie()))
                .andExpect(status().isNoContent());
    }

    private void assertLastUsedIsNull(String projectId, String keyId) {
        assertThat(apiKeyRepository.findByProjectIdAndId(UUID.fromString(projectId), UUID.fromString(keyId)))
                .get()
                .extracting("lastUsedAt")
                .isNull();
    }

    private String createProject(LoginSession owner, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Runtime Test\"}"))
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
