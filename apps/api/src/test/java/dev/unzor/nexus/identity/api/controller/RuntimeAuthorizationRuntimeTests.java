package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validación runtime de los endpoints del API de proyecto que el SDK de Nexus
 * (spec §18) consume: declaración declarativa de permisos
 * ({@code POST /api/v1/permissions/declare}, scope {@code permissions:declare}) y
 * snapshot de autorización ({@code GET /api/v1/authz/users/{userId}/snapshot},
 * scope {@code authz:snapshot}). Cubre auth (API key) + scope + comportamiento.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RuntimeAuthorizationRuntimeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void declareCreatesPermissionsAndMarksMissingForSameApp() throws Exception {
        String email = unique("dec");
        LoginSession owner = login(email);
        String projectId = createProject(owner, randomSlug("dec"));
        String key = createKey(owner, projectId,
                "{\"name\":\"SDK\",\"scopes\":[\"permissions:declare\"],\"expiresAt\":null}");

        // Primer ciclo: la app "api" declara dos permisos.
        mockMvc.perform(post("/api/v1/permissions/declare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .header("X-Nexus-App", "api")
                        .content("[{\"key\":\"orders.read\",\"label\":\"Ver pedidos\"},"
                                + "{\"key\":\"orders.cancel\",\"label\":\"Cancelar pedidos\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declared").value(2))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.markedMissing").value(0));

        // Segundo ciclo: la misma app suelta orders.read → orders.read queda missing.
        mockMvc.perform(post("/api/v1/permissions/declare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .header("X-Nexus-App", "api")
                        .content("[{\"key\":\"orders.cancel\"},{\"key\":\"orders.refund\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declared").value(2))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.markedMissing").value(1));
    }

    @Test
    void declareDoesNotMarkOtherAppsPermissionsMissing() throws Exception {
        String email = unique("multi");
        LoginSession owner = login(email);
        String projectId = createProject(owner, randomSlug("multi"));
        String key = createKey(owner, projectId,
                "{\"name\":\"SDK\",\"scopes\":[\"permissions:declare\"],\"expiresAt\":null}");

        // App "api" declara orders.read.
        mockMvc.perform(post("/api/v1/permissions/declare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .header("X-Nexus-App", "api")
                        .content("[{\"key\":\"orders.read\"}]"))
                .andExpect(status().isOk());

        // App "worker" declara un conjunto distinto: NO debe marcar orders.read (de api) como missing.
        mockMvc.perform(post("/api/v1/permissions/declare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .header("X-Nexus-App", "worker")
                        .content("[{\"key\":\"jobs.run\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedMissing").value(0));
    }

    @Test
    void declareWithoutAppIdentityDoesNotReconcile() throws Exception {
        String email = unique("noapp");
        LoginSession owner = login(email);
        String projectId = createProject(owner, randomSlug("noapp"));
        String key = createKey(owner, projectId,
                "{\"name\":\"SDK\",\"scopes\":[\"permissions:declare\"],\"expiresAt\":null}");

        mockMvc.perform(post("/api/v1/permissions/declare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content("[{\"key\":\"a.read\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedMissing").value(0)); // sin app → no reconcilia (safe)
    }

    @Test
    void declareRejectsLabelExceedingColumnSize() throws Exception {
        String email = unique("label");
        LoginSession owner = login(email);
        String projectId = createProject(owner, randomSlug("label"));
        String key = createKey(owner, projectId,
                "{\"name\":\"SDK\",\"scopes\":[\"permissions:declare\"],\"expiresAt\":null}");
        String tooLong = "x".repeat(121); // la columna label es VARCHAR(120)

        mockMvc.perform(post("/api/v1/permissions/declare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .header("X-Nexus-App", "api")
                        .content("[{\"key\":\"ok\",\"label\":\"" + tooLong + "\"}]"))
                .andExpect(status().isBadRequest()); // 400 limpio (no 500 por exceder VARCHAR(120))
    }

    @Test
    void declareRejectsMissingScope() throws Exception {
        String email = unique("dec-scope");
        LoginSession owner = login(email);
        String projectId = createProject(owner, randomSlug("dec"));
        String key = createKey(owner, projectId,
                "{\"name\":\"SDK\",\"scopes\":[\"other:thing\"],\"expiresAt\":null}");

        mockMvc.perform(post("/api/v1/permissions/declare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content("[{\"key\":\"orders.read\"}]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("missing_scope"));
    }

    @Test
    void snapshotReturnsEffectivePermissionsRolesAndAuthzVersion() throws Exception {
        String email = unique("snap");
        LoginSession owner = login(email);
        String projectId = createProject(owner, randomSlug("snap"));
        String key = createKey(owner, projectId,
                "{\"name\":\"SDK\",\"scopes\":[\"authz:snapshot\"],\"expiresAt\":null}");

        UUID userId = seedUserWithRole(UUID.fromString(projectId), "snap-role", "snap.read");

        mockMvc.perform(get("/api/v1/authz/users/{userId}/snapshot", userId)
                        .header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.authzVersion").value(1))
                .andExpect(jsonPath("$.roles[0]").value("snap-role"))
                .andExpect(jsonPath("$.permissions[0]").value("snap.read"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void snapshotReturnsDenyForUnknownUser() throws Exception {
        String email = unique("snap-unknown");
        LoginSession owner = login(email);
        String projectId = createProject(owner, randomSlug("snapu"));
        String key = createKey(owner, projectId,
                "{\"name\":\"SDK\",\"scopes\":[\"authz:snapshot\"],\"expiresAt\":null}");

        mockMvc.perform(get("/api/v1/authz/users/{userId}/snapshot", UUID.randomUUID())
                        .header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authzVersion").value(-1))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void snapshotRejectsMissingKey() throws Exception {
        mockMvc.perform(get("/api/v1/authz/users/{userId}/snapshot", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_api_key"));
    }

    // --- helpers -----------------------------------------------------------------

    /** Siembra un ProjectUser + un rol con un permiso + la asignación, vía JDBC. */
    private UUID seedUserWithRole(UUID projectId, String roleKey, String permissionKey) {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        String hash = new BCryptPasswordEncoder().encode("ignored");
        jdbc.update("INSERT INTO project_users (id, project_id, email, username, password_hash, "
                + "display_name, status, email_verified_at, authz_version, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?, 'ACTIVE', ?, 1, ?, ?)",
                userId, projectId, "snap-" + userId + "@nexus.test", "snap-" + userId, hash,
                "Snap User", now, now, now);
        jdbc.update("INSERT INTO project_permissions (id, project_id, key, label, description, source, "
                + "enabled, deprecated, missing_from_last_sync, last_declared_at, created_at, updated_at) "
                + "VALUES (?,?,?,?,?, 'WEB', true, false, false, ?, ?, ?)",
                UUID.randomUUID(), projectId, permissionKey, permissionKey, null, now, now, now);
        jdbc.update("INSERT INTO project_roles (id, project_id, system, key, label, description, "
                + "created_at, updated_at) VALUES (?,?, false, ?, ?, ?, ?, ?)",
                roleId, projectId, roleKey, roleKey, null, now, now);
        jdbc.update("INSERT INTO project_role_permissions (id, project_id, role_id, permission_key, "
                + "created_at) VALUES (?,?,?,?, ?)",
                UUID.randomUUID(), projectId, roleId, permissionKey, now);
        jdbc.update("INSERT INTO project_user_roles (id, project_id, project_user_id, role_id, "
                + "created_at) VALUES (?,?,?,?, ?)",
                UUID.randomUUID(), projectId, userId, roleId, now);
        return userId;
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

    private String createProject(LoginSession owner, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Authz Test\"}"))
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
        registerAccount(email);
        Cookie csrfCookie = fetchCsrfCookie();
        MvcResult loginResult = mockMvc.perform(post("/api/panel/v1/session/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .header("User-Agent", "Mozilla/5.0 (Test Runner)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"plain-password\"}"))
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
