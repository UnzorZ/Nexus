package dev.unzor.nexus.audit.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validación runtime del log de auditoría: las mutaciones de los módulos
 * (projects, apikeys, members) emiten {@code AuditEvent} que el módulo audit
 * persiste, y el panel los lista por proyecto. Cubre también el control de
 * acceso ({@code requireAccess}) y el acotado por {@code since}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditEmissionRuntimeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    void auditRecordsProjectKeyAndMemberEventsForOwner() throws Exception {
        String ownerEmail = unique("aud");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("aud"));
        createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        // Invita a una segunda cuenta (debe existir para que AccountDirectory la
        // encuentre) — ejercita la emisión del módulo members. El endpoint responde 200
        // OK (uniforme, anti-enumeración): existe o no la cuenta, el status es el mismo.
        String memberEmail = unique("member");
        registerAccount(memberEmail);
        mockMvc.perform(post("/api/panel/v1/projects/{projectId}/members", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"email\":\"" + memberEmail + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/audit", projectId)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.action =='project.created')]").exists())
                .andExpect(jsonPath("$.items[?(@.action =='api_key.created')]").exists())
                .andExpect(jsonPath("$.items[?(@.action =='member.invited')]").exists())
                .andExpect(jsonPath("$.items[?(@.action =='api_key.created')].severity")
                        .value(hasItem("INFO")))
                .andExpect(jsonPath("$.items[?(@.action =='api_key.created')].actorType")
                        .value(hasItem("NEXUS_ACCOUNT")))
                .andExpect(jsonPath("$.items[?(@.action =='api_key.created')].ip").exists());
    }

    @Test
    void auditRejectsNonMember() throws Exception {
        String ownerEmail = unique("own");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("own"));

        String otherEmail = unique("other");
        registerAccount(otherEmail);
        LoginSession other = login(otherEmail);

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/audit", projectId)
                        .header("X-XSRF-TOKEN", other.csrfToken())
                        .cookie(other.csrfCookie(), other.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("permission_denied"));
    }

    @Test
    void auditSinceBoundsResults() throws Exception {
        String ownerEmail = unique("since");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("since"));
        createKey(owner, projectId,
                "{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}");

        // Un `since` futuro no casa ningún evento (los occurredAt son en el pasado).
        String future = Instant.now().plusSeconds(3600).toString();
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/audit", projectId)
                        .param("since", future)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]").doesNotExist());

        // Sin `since` sí aparecen.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/audit", projectId)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.action =='api_key.created')]").exists());
    }

    // --- helpers -----------------------------------------------------------------

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
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Audit Test\"}"))
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
