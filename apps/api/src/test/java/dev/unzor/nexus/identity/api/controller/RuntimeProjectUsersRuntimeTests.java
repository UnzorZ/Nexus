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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de proyecto de usuarios ({@code /api/v1/users/**}): el backend de un proyecto
 * gestiona a SUS usuarios finales con su API key. Cubre scope, aislamiento por
 * proyecto, lookup por login OIDC, edición de perfil, suspender/reactivar y
 * auditoría con actor {@code API_KEY}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RuntimeProjectUsersRuntimeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void projectBackendManagesItsOwnUsers() throws Exception {
        LoginSession owner = login(unique("users"));
        String projectId = createProject(owner, randomSlug("users"));
        String key = createKey(owner, projectId,
                "{\"name\":\"Backend\",\"scopes\":[\"users:read\",\"users:write\"],\"expiresAt\":null}");
        UUID userId = seedUserWithRole(UUID.fromString(projectId), "member", "files.read");
        String username = "snap-" + userId;

        // Lookup by the OIDC login (the username here).
        mockMvc.perform(get("/api/v1/users/lookup").param("login", username).header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(patch("/api/v1/users/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content("{\"displayName\":\"Nuevo Nombre\",\"username\":\"" + username + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nuevo Nombre"));

        mockMvc.perform(post("/api/v1/users/{userId}/suspend", userId).header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(post("/api/v1/users/{userId}/reactivate", userId).header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/v1/users/{userId}/revoke-tokens", userId).header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/{userId}/password-reset", userId).header("X-Nexus-Api-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true));

        // The suspension is audited with the API key as actor.
        Integer byKey = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'project_user.suspended' AND actor_type = 'API_KEY' "
                        + "AND resource_id = ?", Integer.class, userId.toString());
        org.assertj.core.api.Assertions.assertThat(byKey).isEqualTo(1);
    }

    @Test
    void readOnlyKeyCannotMutateAndOtherProjectsAreInvisible() throws Exception {
        LoginSession owner = login(unique("users-ro"));
        String projectId = createProject(owner, randomSlug("usersro"));
        String otherProject = createProject(owner, randomSlug("usersot"));
        String readKey = createKey(owner, projectId,
                "{\"name\":\"RO\",\"scopes\":[\"users:read\"],\"expiresAt\":null}");
        String otherKey = createKey(owner, otherProject,
                "{\"name\":\"Other\",\"scopes\":[\"users:read\",\"users:write\"],\"expiresAt\":null}");
        UUID userId = seedUserWithRole(UUID.fromString(projectId), "member", "files.read");

        mockMvc.perform(get("/api/v1/users/{userId}", userId).header("X-Nexus-Api-Key", readKey))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/{userId}/suspend", userId).header("X-Nexus-Api-Key", readKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("missing_scope"));
        // A key of another project does not see this user.
        mockMvc.perform(get("/api/v1/users/{userId}", userId).header("X-Nexus-Api-Key", otherKey))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/users/{userId}/suspend", userId).header("X-Nexus-Api-Key", otherKey))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/lookup").param("login", "nobody@example.com")
                        .header("X-Nexus-Api-Key", readKey))
                .andExpect(status().isNotFound());
    }

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
