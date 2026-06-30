package dev.unzor.nexus.apikeys.api.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de panel real (CSRF + sesión) sobre la gestión de API keys.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiKeysSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NexusAccountRepository accountRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ownerCanCreateListRotateDisableAndDeleteKeys() throws Exception {
        String ownerEmail = unique("keys-owner");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("keys"));

        // Create: 201, full key returned once.
        CreatedKey first = createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");
        org.assertj.core.api.Assertions.assertThat(first.key).startsWith("nxs_");

        // List: summary present, no secret field.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/api-keys", projectId)
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("CI"))
                .andExpect(jsonPath("$[0].key").doesNotExist());

        // Rotate: new key returned, old one disabled afterwards.
        CreatedKey rotated = createKeyViaRotate(owner, projectId, first.id);
        org.assertj.core.api.Assertions.assertThat(rotated.key).startsWith("nxs_");
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/api-keys", projectId)
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '" + first.id + "')].status").value("DISABLED"));

        // Disable the rotated key.
        mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/api-keys/{keyId}", projectId, rotated.id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"name\":\"CI\",\"status\":\"DISABLED\",\"expiresAt\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        // Delete the first key.
        mockMvc.perform(delete("/api/panel/v1/projects/{projectId}/api-keys/{keyId}", projectId, first.id)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie()))
                .andExpect(status().isNoContent());
    }

    @Test
    void memberCannotCreateKey() throws Exception {
        String ownerEmail = unique("keys-owner2");
        String memberEmail = unique("keys-member");
        registerAccount(ownerEmail);
        registerAccount(memberEmail);
        LoginSession owner = login(ownerEmail);
        LoginSession member = login(memberEmail);
        String projectId = createProject(owner, randomSlug("keys"));
        addMember(projectId, memberEmail, ProjectMembershipRole.MEMBER);

        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/api-keys", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", member.csrfToken())
                        .cookie(member.csrfCookie(), member.sessionCookie())
                        .content("{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("permission_denied"));
    }

    // --- helpers -----------------------------------------------------------------

    private record CreatedKey(String id, String key) {
    }

    private CreatedKey createKey(LoginSession owner, String projectId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects/{projectId}/api-keys", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractKey(result);
    }

    private CreatedKey createKeyViaRotate(LoginSession owner, String projectId, String keyId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects/{projectId}/api-keys/{keyId}/rotate", projectId, keyId)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn();
        return extractKey(result);
    }

    private CreatedKey extractKey(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CreatedKey(root.at("/summary/id").asText(), root.at("/key").asText());
    }

    private void addMember(String projectId, String email, ProjectMembershipRole role) {
        UUID accountId = accountRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        membershipRepository.save(new ProjectMembership(UUID.fromString(projectId), accountId, role));
    }

    private String createProject(LoginSession owner, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Keys Test\"}"))
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
        MvcResult loginResult = mockMvc.perform(post("/panel/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .header("User-Agent", "Mozilla/5.0 (Test Runner)")
                        .param("username", email)
                        .param("password", "plain-password"))
                .andExpect(status().is3xxRedirection())
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
