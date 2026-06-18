package dev.unzor.nexus.admin.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PanelSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NexusAccountRepository accountRepository;

    @Test
    void panelLoginPageIsPublic() throws Exception {
        mockMvc.perform(get("/panel/login"))
                .andExpect(status().isOk());
    }

    @Test
    void panelApiWithoutSessionReturnsUnauthorizedWithoutRedirect() throws Exception {
        mockMvc.perform(get("/api/panel/v1/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/panel/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson("owner@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationWithCsrfCookieAndHeaderIsAccepted() throws Exception {
        CsrfTokens csrf = fetchCsrf();

        mockMvc.perform(post("/api/panel/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", csrf.token())
                        .cookie(csrf.cookie())
                        .content(accountJson("csrf-owner@example.com")))
                .andExpect(status().isCreated());
    }

    @Test
    void legacyLoginRouteIsNotUsedByPanelChain() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRouteIsReservedForFutureInstanceAdministration() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isForbidden());
    }

    @Test
    void successfulLoginWithoutContinueRedirectsToFrontendDashboard() throws Exception {
        String email = "login-success-" + UUID.randomUUID() + "@example.com";
        registerAccount(email);
        CsrfTokens csrf = fetchCsrf();

        mockMvc.perform(post("/panel/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .param("username", email)
                        .param("password", "plain-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/dashboard"));
    }

    @Test
    void nexusAccountWithoutInstanceAdminCanLoginToPanel() throws Exception {
        registerAccount("bootstrap-" + UUID.randomUUID() + "@example.com");
        String email = "member-" + UUID.randomUUID() + "@example.com";
        registerAccount(email);

        var account = accountRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(account.isInstanceAdmin()).isFalse();

        loginAndReturn(email);
    }

    @Test
    void failedLoginPreservesSafeContinueParameter() throws Exception {
        CsrfTokens csrf = fetchCsrf();
        String continueUrl = "http://localhost:3000/dashboard";

        mockMvc.perform(post("/panel/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .param("username", "missing@example.com")
                        .param("password", "wrong-password")
                        .param("continue", continueUrl))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String location = result.getResponse().getHeader("Location");
                    assertThat(location)
                            .contains("error=true")
                            .contains("continue=");
                });
    }

    @Test
    void successfulLoginIgnoresMaliciousContinueParameter() throws Exception {
        String email = "login-safe-" + UUID.randomUUID() + "@example.com";
        registerAccount(email);
        CsrfTokens csrf = fetchCsrf();

        mockMvc.perform(post("/panel/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .param("username", email)
                        .param("password", "plain-password")
                        .param("continue", "https://evil.example/phish"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/dashboard"));
    }

    @Test
    void apiLogoutRequiresCsrf() throws Exception {
        String email = "logout-csrf-" + UUID.randomUUID() + "@example.com";
        registerAccount(email);
        MvcResult loginResult = loginAndReturn(email);

        mockMvc.perform(post("/api/panel/v1/session/logout")
                        .cookie(loginResult.getResponse().getCookies()))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiLogoutInvalidatesSessionWithCsrf() throws Exception {
        String email = "logout-ok-" + UUID.randomUUID() + "@example.com";
        registerAccount(email);
        MvcResult loginResult = loginAndReturn(email);

        Cookie sessionCookie = loginResult.getResponse().getCookie("JSESSIONID");
        if (sessionCookie == null) {
            throw new IllegalStateException("JSESSIONID not issued after login");
        }

        CsrfTokens logoutCsrf = fetchCsrfWithCookies(sessionCookie);
        mockMvc.perform(post("/api/panel/v1/session/logout")
                        .cookie(sessionCookie)
                        .cookie(logoutCsrf.cookie())
                        .header("X-XSRF-TOKEN", logoutCsrf.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/panel/v1/me")
                        .cookie(sessionCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
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

    @Test
    void csrfEndpointReturnsTokenInTheBodyForCrossOriginClients() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        // The token must be available in the body (not only the cookie) so a
        // cross-origin SPA — which cannot read document.cookie — can echo it in
        // the X-XSRF-TOKEN header. The cookie is still issued for double-submit.
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("X-XSRF-TOKEN");
        assertThat(body).contains("\"token\"");
        assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
    }

    private MvcResult loginAndReturn(String email) throws Exception {
        CsrfTokens csrf = fetchCsrf();
        return mockMvc.perform(post("/panel/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .param("username", email)
                        .param("password", "plain-password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    private CsrfTokens fetchCsrf() throws Exception {
        return fetchCsrfWithSession(null);
    }

    private CsrfTokens fetchCsrfWithSession(MockHttpSession session) throws Exception {
        var requestBuilder = get("/api/panel/v1/csrf");
        if (session != null) {
            requestBuilder = requestBuilder.session(session);
        }

        MvcResult csrfResult = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null || cookie.getValue() == null) {
            throw new IllegalStateException("XSRF-TOKEN cookie was not issued by /api/panel/v1/csrf");
        }
        return new CsrfTokens(cookie.getValue(), cookie);
    }

    private CsrfTokens fetchCsrfWithCookies(Cookie... cookies) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/panel/v1/csrf").cookie(cookies))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null || cookie.getValue() == null) {
            throw new IllegalStateException("XSRF-TOKEN cookie was not issued by /api/panel/v1/csrf");
        }
        return new CsrfTokens(cookie.getValue(), cookie);
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

    private record CsrfTokens(String token, Cookie cookie) {
    }
}
