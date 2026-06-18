package dev.unzor.nexus.admin.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.admin.infrastructure.security.NexusAccountPrincipal;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Verifies that the password hash is never stored in the session (and therefore in
 * Redis) and that the principal recovered from Spring Session after login does not
 * retain it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PanelSessionCredentialsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NexusAccountRepository accountRepository;

    @Autowired
    private RedisIndexedSessionRepository sessionRepository;

    @Test
    void principalRetrievedFromSpringSessionHasNoPasswordHash() throws Exception {
        String email = "creds-" + UUID.randomUUID() + "@example.com";
        registerAccount(email);
        Cookie sessionCookie = login(email);

        var account = accountRepository.findByEmailIgnoreCase(email).orElseThrow();

        // Recover the session exactly as Spring Session would deserialize it from Redis.
        var sessions = sessionRepository.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                account.getId().toString());
        assertThat(sessions).isNotEmpty();

        var session = sessions.values().iterator().next();
        Object contextAttr = session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(contextAttr).isInstanceOf(SecurityContext.class);
        SecurityContext securityContext = (SecurityContext) contextAttr;

        assertThat(securityContext.getAuthentication().getPrincipal())
                .isInstanceOf(NexusAccountPrincipal.class);
        NexusAccountPrincipal principal = (NexusAccountPrincipal) securityContext.getAuthentication().getPrincipal();
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.accountId()).isEqualTo(account.getId());
    }

    @Test
    void eraseCredentialsRemovesPassword() {
        NexusAccountPrincipal principal = new NexusAccountPrincipal(
                UUID.randomUUID(), "user@example.com", "hash-value", java.util.List.of(), true);

        assertThat(principal.getPassword()).isEqualTo("hash-value");

        principal.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
    }

    private void registerAccount(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String token = csrfCookie.getValue();

        mockMvc.perform(post("/api/panel/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", token)
                        .cookie(csrfCookie)
                        .content(accountJson(email)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
    }

    private Cookie login(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String token = csrfCookie.getValue();

        MvcResult loginResult = mockMvc.perform(post("/panel/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", token)
                        .header("User-Agent", "Mozilla/5.0 (Test Runner)")
                        .param("username", email)
                        .param("password", "plain-password"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection())
                .andReturn();

        Cookie session = loginResult.getResponse().getCookie("JSESSIONID");
        assertThat(session).as("JSESSIONID issued").isNotNull();
        return session;
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
}
