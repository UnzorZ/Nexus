package dev.unzor.nexus.admin.api.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.admin.application.configuration.PanelSessionConfiguration;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification of Redis-backed panel sessions and revocation (scenarios
 * 1–7 of the shared-Redis spec).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PanelSessionManagementTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NexusAccountRepository accountRepository;

    @Autowired
    private RedisIndexedSessionRepository sessionRepository;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginCreatesRedisSessionAndCookieAuthenticatesNextRequest() throws Exception {
        String email = unique("redis-login");
        registerAccount(email);
        LoginSession login = login(email);

        mockMvc.perform(get("/api/panel/v1/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(login.sessionCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void jsonLoginRotatesSessionIdAndInvalidatesThePreLoginId() throws Exception {
        String email = unique("json-rotate");
        registerAccount(email);

        // Simula una sesión anónima fijada previamente (session fixation): el
        // atacante la crea en Redis y la víctima reutiliza su JSESSIONID al
        // loguearse por JSON.
        var planted = sessionRepository.createSession();
        sessionRepository.save(planted);
        Cookie preLoginSession = new Cookie("JSESSIONID", planted.getId());

        // La cookie CSRF es independiente de la sesión (CookieCsrfTokenRepository),
        // así que GET /csrf no crea sesión: la pedimos aparte.
        MvcResult csrfResult = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie xsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).as("XSRF-TOKEN issued").isNotNull();

        // Login JSON reutilizando la sesión anónima (vector de session fixation).
        MvcResult loginResult = mockMvc.perform(post("/api/panel/v1/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(xsrf, preLoginSession)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .header("User-Agent", "Mozilla/5.0 (Test Runner) Nexus/1.0")
                        .content(loginJson(email)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie postLoginSession = cookieByName(loginResult, "JSESSIONID");
        assertThat(postLoginSession).as("JSON login should reissue JSESSIONID after rotation").isNotNull();
        // Rotación: el id autenticado difiere del anónimo pre-login.
        assertThat(postLoginSession.getValue()).isNotEqualTo(preLoginSession.getValue());

        // El nuevo id está autenticado y conserva el principal.
        mockMvc.perform(get("/api/panel/v1/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(postLoginSession))
                .andExpect(status().isOk());

        // El id anónimo pre-login ya no sirve: session fixation cerrado.
        mockMvc.perform(get("/api/panel/v1/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(preLoginSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sessionIsRecoverableThroughASecondRepositoryInstanceOnSameRedis() throws Exception {
        String email = unique("second-repo");
        registerAccountQuiet(email);
        var account = accountRepository.findByEmailIgnoreCase(email).orElseThrow();

        // Two independent repository instances, wired to the same Redis connection pool and
        // the same namespace as the application's repository.
        var operations = (org.springframework.data.redis.core.RedisOperations<String, Object>)
                (org.springframework.data.redis.core.RedisOperations<?, ?>) redisTemplate;

        RedisIndexedSessionRepository writer = new RedisIndexedSessionRepository(operations);
        writer.setRedisKeyNamespace("nexus:session:");
        writer.setDefaultMaxInactiveInterval(604800);

        var created = writer.createSession();
        created.setAttribute(PanelSessionConfiguration.ACCOUNT_ID, account.getId().toString());
        created.setAttribute(PanelSessionConfiguration.SESSION_PUBLIC_ID, UUID.randomUUID().toString());
        writer.save(created);

        RedisIndexedSessionRepository reader = new RedisIndexedSessionRepository(operations);
        reader.setRedisKeyNamespace("nexus:session:");
        reader.setDefaultMaxInactiveInterval(604800);

        assertThat(reader.findById(created.getId())).isNotNull();
    }

    @Test
    void twoSessionsOfAccountAreListedAndOthersAreNot() throws Exception {
        String ownerEmail = unique("two-owner");
        registerAccount(ownerEmail);
        LoginSession first = login(ownerEmail);
        LoginSession second = login(ownerEmail);

        String otherEmail = unique("two-other");
        registerAccount(otherEmail);
        login(otherEmail);

        MvcResult result = mockMvc.perform(get("/api/panel/v1/sessions")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(first.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.findValuesAsString("id"))
                .containsExactlyInAnyOrder(first.publicSessionId(), second.publicSessionId());
        assertThat(items.get(0).get("current").asBoolean()).isTrue();
    }

    @Test
    void revokingOneSessionInvalidatesItsNextRequest() throws Exception {
        String email = unique("revoke-one");
        registerAccount(email);
        LoginSession login = login(email);

        mockMvc.perform(delete("/api/panel/v1/sessions/" + login.publicSessionId())
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(login.sessionCookie())
                        .header("X-XSRF-TOKEN", login.csrfToken())
                        .cookie(login.csrfCookie()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/panel/v1/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(login.sessionCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokingAllInvalidatesEverySessionOfTheAccount() throws Exception {
        String ownerEmail = unique("revoke-all-owner");
        registerAccount(ownerEmail);
        LoginSession first = login(ownerEmail);
        LoginSession second = login(ownerEmail);

        String otherEmail = unique("revoke-all-other");
        registerAccount(otherEmail);
        LoginSession other = login(otherEmail);

        mockMvc.perform(delete("/api/panel/v1/sessions")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(first.sessionCookie())
                        .header("X-XSRF-TOKEN", first.csrfToken())
                        .cookie(first.csrfCookie()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/panel/v1/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(first.sessionCookie()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/panel/v1/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(second.sessionCookie()))
                .andExpect(status().isUnauthorized());

        // Other account is untouched.
        mockMvc.perform(get("/api/panel/v1/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(other.sessionCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void responsesDoNotExposeInternalIdsOrSecurityContext() throws Exception {
        String email = unique("no-leak");
        registerAccount(email);
        LoginSession login = login(email);

        MvcResult me = mockMvc.perform(get("/api/panel/v1/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(login.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult sessions = mockMvc.perform(get("/api/panel/v1/sessions")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(login.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn();

        String meBody = me.getResponse().getContentAsString();
        String sessionsBody = sessions.getResponse().getContentAsString();
        String cookieValue = login.sessionCookie().getValue();

        assertThat(meBody).doesNotContain("SecurityContext", "authorities", "XSRF-TOKEN");
        assertThat(sessionsBody).doesNotContain("SecurityContext", "authorities", "XSRF-TOKEN");
        assertThat(meBody).doesNotContain(cookieValue);
        assertThat(sessionsBody).doesNotContain(cookieValue);
        assertThat(sessionsBody).contains(login.publicSessionId());
    }

    @Test
    void sessionHasSevenDayTtlAndIsIndexedByAccountId() throws Exception {
        String email = unique("ttl-index");
        registerAccount(email);
        LoginSession login = login(email);
        var account = accountRepository.findByEmailIgnoreCase(email).orElseThrow();

        var byAccount = sessionRepository.findByIndexNameAndIndexValue(
                org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                account.getId().toString());

        assertThat(byAccount.values()).hasSize(1);
        var session = byAccount.values().iterator().next();
        assertThat(session.getMaxInactiveInterval().toSeconds()).isEqualTo(604800);

        mockMvc.perform(get("/api/panel/v1/sessions")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(login.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maxInactiveIntervalSeconds").value(604800));
    }

    // --- helpers -----------------------------------------------------------------

    private void registerAccount(String email) throws Exception {
        registerAccountQuiet(email);
    }

    private void registerAccountQuiet(String email) throws Exception {
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
                        .header("User-Agent", "Mozilla/5.0 (Test Runner) Nexus/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\""+email+"\",\"password\":\"plain-password\"}"))
                        .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = cookieByName(loginResult, "JSESSIONID");
        if (sessionCookie == null) {
            throw new IllegalStateException("JSESSIONID not issued after login");
        }

        // Resolve the public session id by reading the management API, which lists the
        // current session first and marks it with current=true.
        MvcResult sessions = mockMvc.perform(get("/api/panel/v1/sessions")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(sessions.getResponse().getContentAsString());
        String publicId = null;
        for (JsonNode item : items) {
            if (item.get("current").asBoolean()) {
                publicId = item.get("id").asText();
                break;
            }
        }
        if (publicId == null) {
            throw new IllegalStateException("Could not resolve current session public id");
        }

        return new LoginSession(sessionCookie, publicId, csrf.token(), csrf.cookie());
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
                  "displayName": "Owner"
                }
                """.formatted(email);
    }

    private static String loginJson(String email) {
        return """
                {
                  "email": "%s",
                  "password": "plain-password"
                }
                """.formatted(email);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private record CsrfTokens(String token, Cookie cookie) {
    }

    private record LoginSession(Cookie sessionCookie, String publicSessionId,
                                String csrfToken, Cookie csrfCookie) {
    }
}
