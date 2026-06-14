package dev.unzor.nexus.admin.application.events;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.admin.application.service.PanelSessionService;
import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that lifecycle-triggered session revocation is delivered reliably.
 *
 * <p>{@link NexusAccount} is a Spring Data {@code AbstractAggregateRoot}; it publishes
 * {@code NexusAccountSessionsRevocationRequested} only when the aggregate is saved
 * through the repository within a transaction. Spring Modulith persists the publication
 * in PostgreSQL. Delivery is driven by {@link IncompleteEventPublications}, which the
 * application triggers at startup and on a bounded schedule via
 * {@code PanelSessionRevocationRepublisher}. If the delivery fails (e.g. Redis is
 * temporarily unavailable right after commit), the publication stays incomplete and is
 * re-delivered later. The revocation operation is idempotent.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PanelSessionRevocationReliabilityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NexusAccountRepository accountRepository;

    @Autowired
    private RedisIndexedSessionRepository sessionRepository;

    @Autowired
    private PanelSessionRevocationRepublisher revocationRepublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private PanelSessionService panelSessionService;

    @Test
    void suspendingAccountRevokesItsSessionsOnDelivery() throws Exception {
        String email = emailFor("revoke-base");
        Cookie sessionCookie = loginAndRegister(email);
        UUID accountId = accountRepository.findByEmailIgnoreCase(email).orElseThrow().getId();

        assertThat(sessionsFor(accountId)).isNotEmpty();

        suspendAccount(accountId);

        // The normal listener is asynchronous; wait until its initial delivery completes.
        await("initial session revocation", () -> sessionsFor(accountId).isEmpty());

        assertThat(sessionsFor(accountId)).isEmpty();
        mockMvc.perform(get("/api/panel/v1/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revocationIsRedeliveredAfterFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        // Fail the first delivery; let subsequent ones proceed to the real service.
        Mockito.doAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("Redis temporarily unavailable");
            }
            return invocation.callRealMethod();
        }).when(panelSessionService).revokeAllForAccount(Mockito.any(UUID.class));

        String email = emailFor("revoke-retry");
        Cookie sessionCookie = loginAndRegister(email);
        UUID accountId = accountRepository.findByEmailIgnoreCase(email).orElseThrow().getId();

        assertThat(sessionsFor(accountId)).isNotEmpty();

        suspendAccount(accountId);

        // Wait until the asynchronous first attempt has failed and Modulith has left the
        // publication incomplete before asking the republisher to deliver it again.
        await("failed initial revocation attempt", () -> calls.get() >= 1);
        assertThat(sessionsFor(accountId)).isNotEmpty();

        // Re-deliver through the same filtered and bounded path used on application startup.
        revocationRepublisher.resubmitOnStartup();
        await("retried session revocation", () -> sessionsFor(accountId).isEmpty());

        assertThat(sessionsFor(accountId)).isEmpty();
        mockMvc.perform(get("/api/panel/v1/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
        assertThat(calls.get()).isGreaterThanOrEqualTo(2);
    }

    private static void await(String description, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }

    private void suspendAccount(UUID accountId) {
        transactionTemplate.executeWithoutResult(status -> {
            NexusAccount managed = accountRepository.findById(accountId).orElseThrow();
            managed.suspend();
            accountRepository.save(managed);
        });
    }

    private java.util.Map<String, ?> sessionsFor(UUID accountId) {
        return sessionRepository.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, accountId.toString());
    }

    private Cookie loginAndRegister(String email) throws Exception {
        registerAccount(email);
        return login(email);
    }

    private static String emailFor(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private void registerAccount(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String token = csrfCookie.getValue();

        mockMvc.perform(post("/api/panel/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", token)
                        .cookie(csrfCookie)
                        .content(accountJson(email)))
                .andExpect(status().isCreated());
    }

    private Cookie login(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String token = csrfCookie.getValue();

        MvcResult loginResult = mockMvc.perform(post("/panel/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", token)
                        .header("User-Agent", "Mozilla/5.0 (Test Runner)")
                        .param("username", email)
                        .param("password", "plain-password"))
                .andExpect(status().is3xxRedirection())
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
