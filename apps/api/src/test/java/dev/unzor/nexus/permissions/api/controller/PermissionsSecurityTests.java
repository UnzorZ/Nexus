package dev.unzor.nexus.permissions.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de panel real (CSRF + sesión) sobre el catálogo de permisos y los roles:
 * autorización, duplicados y validación de claves.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PermissionsSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @Autowired
    private NexusAccountRepository accountRepository;

    @Test
    void ownerCanDeclarePermissionsRolesAndAssign() throws Exception {
        String ownerEmail = unique("perms-owner");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("perms"));

        // Declara un permiso en el catálogo.
        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/permissions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"key\":\"orders.cancel\",\"label\":\"Cancel order\",\"description\":\"Cancel an order\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("orders.cancel"))
                .andExpect(jsonPath("$.source").value("WEB"));

        // Crea un rol y le asigna el permiso declarado + un comodín.
        String roleId = createResource(owner, projectId, "/roles",
                "{\"key\":\"operator\",\"label\":\"Operator\",\"description\":null}");

        mockMvc.perform(put("/api/panel/v1/projects/{projectId}/roles/{roleId}/permissions", projectId, roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"permissionKeys\":[\"orders.cancel\",\"orders.*\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionKeys[0]").value("orders.cancel"))
                .andExpect(jsonPath("$.permissionKeys[1]").value("orders.*"));

        // El listado de roles muestra el operador con sus 2 claves.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/roles", projectId)
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].key").value("operator"))
                .andExpect(jsonPath("$[0].permissionKeys.length()").value(2));
    }

    @Test
    void setPermissionsWithOverlapDoesNotConflict() throws Exception {
        String ownerEmail = unique("perms-overlap");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("perms"));
        String roleId = createResource(owner, projectId, "/roles",
                "{\"key\":\"operator\",\"label\":\"Operator\",\"description\":null}");

        // First grant set.
        mockMvc.perform(put("/api/panel/v1/projects/{projectId}/roles/{roleId}/permissions", projectId, roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"permissionKeys\":[\"orders.cancel\",\"orders.read\"]}"))
                .andExpect(status().isOk());

        // Replace with an overlapping set (keeps the two, adds one). A deferred
        // delete-by-role flush would insert the kept keys before deleting the old
        // rows and trip the (role_id, permission_key) unique constraint -> 500.
        mockMvc.perform(put("/api/panel/v1/projects/{projectId}/roles/{roleId}/permissions", projectId, roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"permissionKeys\":[\"orders.cancel\",\"orders.read\",\"orders.refund\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionKeys.length()").value(3));
    }

    @Test
    void invalidPermissionFormsAreRejected() throws Exception {
        String ownerEmail = unique("perms-invalid");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("perms"));
        String roleId = createResource(owner, projectId, "/roles",
                "{\"key\":\"operator\",\"label\":\"Operator\",\"description\":null}");

        // Wildcards only as global '*' or terminal 'ns.*'; a mid/leading '*'
        // (orders.*.read) is not a documented positive-permission form.
        mockMvc.perform(put("/api/panel/v1/projects/{projectId}/roles/{roleId}/permissions", projectId, roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"permissionKeys\":[\"orders.*.read\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));

        // A syntactically valid key longer than the 128-char column must be a 400,
        // not a database error at insert time.
        String tooLong = "a".repeat(129);
        mockMvc.perform(put("/api/panel/v1/projects/{projectId}/roles/{roleId}/permissions", projectId, roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"permissionKeys\":[\"" + tooLong + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void memberCannotDeclarePermissions() throws Exception {
        String ownerEmail = unique("perms-owner2");
        String memberEmail = unique("perms-member");
        registerAccount(ownerEmail);
        registerAccount(memberEmail);
        LoginSession owner = login(ownerEmail);
        LoginSession member = login(memberEmail);
        String projectId = createProject(owner, randomSlug("perms"));
        addMember(projectId, memberEmail, ProjectMembershipRole.MEMBER);

        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/permissions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", member.csrfToken())
                        .cookie(member.csrfCookie(), member.sessionCookie())
                        .content("{\"key\":\"orders.cancel\",\"label\":\"Cancel\",\"description\":null}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("permission_denied"));
    }

    @Test
    void duplicatePermissionKeyReturnsConflict() throws Exception {
        String ownerEmail = unique("perms-owner3");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("perms"));

        createResource(owner, projectId, "/permissions",
                "{\"key\":\"orders.cancel\",\"label\":\"Cancel\",\"description\":null}");

        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/permissions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"key\":\"orders.cancel\",\"label\":\"Other\",\"description\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
    }

    @Test
    void invalidPermissionKeyReturnsValidation() throws Exception {
        String ownerEmail = unique("perms-owner4");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("perms"));

        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/permissions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"key\":\"Orders Cancel\",\"label\":\"Cancel\",\"description\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    // --- helpers -----------------------------------------------------------------

    private String createResource(LoginSession owner, String projectId, String suffix, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects/{projectId}{suffix}", projectId, suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result);
    }

    private String createProject(LoginSession owner, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Permissions Test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result);
    }

    private static String extractId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"id\":\"") + 6;
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private void addMember(String projectId, String email, ProjectMembershipRole role) {
        UUID accountId = accountRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        membershipRepository.save(new ProjectMembership(UUID.fromString(projectId), accountId, role));
    }

    private void registerAccount(String email) throws Exception {
        CsrfTokens csrf = fetchCsrf();
        mockMvc.perform(post("/api/panel/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", csrf.token())
                        .cookie(csrf.cookie())
                        .content(accountJson(email)))
                .andExpect(status().isCreated());
    }

    private LoginSession login(String email) throws Exception {
        CsrfTokens csrf = fetchCsrf();
        MvcResult loginResult = mockMvc.perform(post("/api/panel/v1/session/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .header("User-Agent", "Mozilla/5.0 (Test Runner)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\""+email+"\",\"password\":\"plain-password\"}"))
                        .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = cookieByName(loginResult, "JSESSIONID");
        if (sessionCookie == null) {
            throw new IllegalStateException("JSESSIONID not issued after login");
        }
        return new LoginSession(sessionCookie, csrf.token(), csrf.cookie());
    }

    private CsrfTokens fetchCsrf() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null || cookie.getValue() == null) {
            throw new IllegalStateException("XSRF-TOKEN cookie was not issued");
        }
        return new CsrfTokens(cookie.getValue(), cookie);
    }

    private static Cookie cookieByName(MvcResult result, String name) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    private static String accountJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"plain-password\",\"displayName\":\"Tester\"}";
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static String randomSlug(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record CsrfTokens(String token, Cookie cookie) {
    }

    private record LoginSession(Cookie sessionCookie, String csrfToken, Cookie csrfCookie) {
    }
}
