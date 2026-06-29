package dev.unzor.nexus.modules.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.entity.ProjectMembership;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectModulesSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @Autowired
    private NexusAccountRepository accountRepository;

    @Test
    void ownerCanListAndPatchModules() throws Exception {
        String ownerEmail = unique("modules-owner");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);

        String projectId = createProject(owner, randomSlug("mods"));

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/modules", projectId)
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11));

        mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/modules/{key}", projectId, "vault")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie())
                        .cookie(owner.sessionCookie())
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("vault"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void memberCannotPatchModules() throws Exception {
        String ownerEmail = unique("modules-owner2");
        String memberEmail = unique("modules-member");
        registerAccount(ownerEmail);
        registerAccount(memberEmail);
        LoginSession owner = login(ownerEmail);
        LoginSession member = login(memberEmail);

        String projectId = createProject(owner, randomSlug("mods"));
        addMember(projectId, memberEmail, ProjectMembershipRole.MEMBER);

        mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/modules/{key}", projectId, "vault")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", member.csrfToken())
                        .cookie(member.csrfCookie())
                        .cookie(member.sessionCookie())
                        .content("{\"enabled\": true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("permission_denied"));
    }

    @Test
    void patchWithoutCsrfIsRejected() throws Exception {
        String ownerEmail = unique("modules-owner3");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("mods"));

        mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/modules/{key}", projectId, "vault")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(owner.sessionCookie())
                        .content("{\"enabled\": true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void unknownModuleReturnsNotFound() throws Exception {
        String ownerEmail = unique("modules-owner4");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("mods"));

        mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/modules/{key}", projectId, "bogus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie())
                        .cookie(owner.sessionCookie())
                        .content("{\"enabled\": true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void unknownProjectReturnsForbiddenWithoutMembership() throws Exception {
        registerAccount(unique("modules-bootstrap-admin"));
        String memberEmail = unique("modules-member-noaccess");
        registerAccount(memberEmail);
        LoginSession member = login(memberEmail);
        UUID missingProjectId = UUID.randomUUID();

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/modules", missingProjectId)
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(member.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("permission_denied"));
    }

    @Test
    void archivedProjectCanBePatched() throws Exception {
        String ownerEmail = unique("modules-owner6");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("mods"));

        Project project = projectRepository.findById(UUID.fromString(projectId)).orElseThrow();
        project.archive();
        projectRepository.save(project);

        // Non-ACTIVE projects are NOT blocked (the UI warns instead); the toggle
        // still persists so it takes effect once the project is reactivated.
        mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/modules/{key}", projectId, "vault")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie())
                        .cookie(owner.sessionCookie())
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("vault"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    private void addMember(String projectId, String email, ProjectMembershipRole role) {
        UUID accountId = accountRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        membershipRepository.save(new ProjectMembership(UUID.fromString(projectId), accountId, role));
    }

    private String createProject(LoginSession owner, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie())
                        .cookie(owner.sessionCookie())
                        .content("""
                                {
                                  "slug": "%s",
                                  "name": "Modules Test"
                                }
                                """.formatted(slug)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        int idStart = body.indexOf("\"id\":\"") + 6;
        int idEnd = body.indexOf('"', idStart);
        return body.substring(idStart, idEnd);
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
        return """
                {
                  "email": "%s",
                  "password": "plain-password",
                  "displayName": "Tester"
                }
                """.formatted(email);
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
