package dev.unzor.nexus.identity.application.events;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.admin.application.configuration.PanelSessionConfiguration;
import dev.unzor.nexus.identity.application.service.RecordProjectUserLoginService;
import dev.unzor.nexus.identity.infrastructure.security.MfaPendingTicket;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.projects.application.service.ArchiveProjectService;
import dev.unzor.nexus.projects.application.service.RestoreProjectService;
import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba el límite real PostgreSQL + Spring Session Redis del archivado. Las
 * sesiones anteriores no reaparecen al restaurar el proyecto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectArchivedSessionRevocationIT {

    @Autowired
    private ArchiveProjectService archiveProjectService;

    @Autowired
    private RestoreProjectService restoreProjectService;

    @Autowired
    private RedisIndexedSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private RecordProjectUserLoginService recordLoginService;

    @Test
    void archiveDeletesAuthenticatedAndMfaPendingSessionsPermanentlyWithoutTouchingOtherFamilies() {
        UUID archivedProjectId = UUID.randomUUID();
        UUID activeProjectId = UUID.randomUUID();
        UUID archivedUserId = UUID.randomUUID();
        UUID activeUserId = UUID.randomUUID();
        seedProjectAndUser(archivedProjectId, archivedUserId, "archive-sessions");
        seedProjectAndUser(activeProjectId, activeUserId, "active-sessions");

        var authenticated = authenticatedProjectUserSession(archivedProjectId, archivedUserId);
        var mfaPending = projectUserSession(archivedUserId);
        Instant now = Instant.now();
        mfaPending.setAttribute(NexusSessionAttributes.MFA_PENDING,
                new MfaPendingTicket(archivedUserId, archivedProjectId, now, now.plusSeconds(300)));
        var otherProject = projectUserSession(activeUserId);
        var panel = sessionRepository.createSession();
        panel.setAttribute(PanelSessionConfiguration.ACCOUNT_ID, UUID.randomUUID().toString());

        sessionRepository.save(authenticated);
        sessionRepository.save(mfaPending);
        sessionRepository.save(otherProject);
        sessionRepository.save(panel);

        assertThat(projectSessions(archivedUserId)).containsKeys(authenticated.getId(), mfaPending.getId());

        archiveProjectService.archive(archivedProjectId, UUID.randomUUID());

        assertThat(projectStatus(archivedProjectId)).isEqualTo("ARCHIVED");
        assertThat(sessionRepository.findById(authenticated.getId())).isNull();
        assertThat(sessionRepository.findById(mfaPending.getId())).isNull();
        assertThat(projectSessions(archivedUserId)).isEmpty();
        assertThat(sessionRepository.findById(otherProject.getId())).isNotNull();
        assertThat(sessionRepository.findById(panel.getId())).isNotNull();

        restoreProjectService.restore(archivedProjectId, UUID.randomUUID());

        assertThat(projectStatus(archivedProjectId)).isEqualTo("ACTIVE");
        assertThat(sessionRepository.findById(authenticated.getId())).isNull();
        assertThat(sessionRepository.findById(mfaPending.getId())).isNull();
        assertThat(sessionRepository.findById(otherProject.getId())).isNotNull();
        assertThat(sessionRepository.findById(panel.getId())).isNotNull();
    }

    @Test
    void concurrentArchiveWaitsForLoginSessionToBeIndexedAndThenDeletesIt() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String slug = "concurrent-login-" + projectId;
        String email = userId + "@session.test";
        String passwordHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode("plain-password");
        seedProjectAndUser(projectId, userId, slug, email, passwordHash);

        CountDownLatch sessionPersisted = new CountDownLatch(1);
        CountDownLatch releaseLogin = new CountDownLatch(1);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            // establishSession guarda el SecurityContext antes de registrar el login.
            // Con FlushMode.IMMEDIATE la sesión ya está en Redis aquí, mientras la
            // transacción conserva todavía el lock compartido del proyecto.
            sessionPersisted.countDown();
            if (!releaseLogin.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release concurrent login");
            }
            return null;
        }).when(recordLoginService).recordLogin(projectId, userId);

        Cookie csrfCookie = csrfCookie(slug);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<MvcResult> login = CompletableFuture.supplyAsync(() -> {
                try {
                    return mockMvc.perform(post("/api/p/" + slug + "/login")
                                    .cookie(csrfCookie)
                                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"email\":\"" + email
                                            + "\",\"password\":\"plain-password\"}"))
                            .andExpect(status().isOk())
                            .andReturn();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }, executor);

            assertThat(sessionPersisted.await(10, TimeUnit.SECONDS)).isTrue();
            Map<String, ?> indexedDuringLogin = projectSessions(userId);
            assertThat(indexedDuringLogin).hasSize(1);
            String internalSessionId = indexedDuringLogin.keySet().iterator().next();

            CompletableFuture<Void> archive = CompletableFuture.runAsync(
                    () -> archiveProjectService.archive(projectId, UUID.randomUUID()), executor);

            Thread.sleep(200);
            assertThat(archive).as("archive waits for the login project lock").isNotDone();
            assertThat(projectStatus(projectId)).isEqualTo("ACTIVE");

            releaseLogin.countDown();
            login.get(10, TimeUnit.SECONDS);
            archive.get(10, TimeUnit.SECONDS);

            assertThat(projectStatus(projectId)).isEqualTo("ARCHIVED");
            assertThat(sessionRepository.findById(internalSessionId)).isNull();
            assertThat(projectSessions(userId)).isEmpty();
        } finally {
            releaseLogin.countDown();
        }
    }

    @Test
    void transactionalLoginPreservesLockoutAndMfaPendingOutcomes() throws Exception {
        UUID passwordProjectId = UUID.randomUUID();
        UUID passwordUserId = UUID.randomUUID();
        String passwordSlug = "wrong-password-" + passwordProjectId;
        String passwordEmail = passwordUserId + "@session.test";
        String passwordHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode("plain-password");
        seedProjectAndUser(passwordProjectId, passwordUserId, passwordSlug, passwordEmail, passwordHash);

        Cookie passwordCsrf = csrfCookie(passwordSlug);
        mockMvc.perform(post("/api/p/" + passwordSlug + "/login")
                        .cookie(passwordCsrf)
                        .header("X-XSRF-TOKEN", passwordCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + passwordEmail + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject(
                "SELECT failed_login_attempts FROM project_users WHERE id = ?",
                Integer.class, passwordUserId)).isEqualTo(1);

        UUID mfaProjectId = UUID.randomUUID();
        UUID mfaUserId = UUID.randomUUID();
        String mfaSlug = "mfa-pending-" + mfaProjectId;
        String mfaEmail = mfaUserId + "@session.test";
        seedProjectAndUser(mfaProjectId, mfaUserId, mfaSlug, mfaEmail, passwordHash);
        jdbc.update("UPDATE project_users SET totp_secret_enc = 'encrypted', totp_enabled_at = now() WHERE id = ?",
                mfaUserId);

        Cookie mfaCsrf = csrfCookie(mfaSlug);
        MvcResult mfaResult = mockMvc.perform(post("/api/p/" + mfaSlug + "/login")
                        .cookie(mfaCsrf)
                        .header("X-XSRF-TOKEN", mfaCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + mfaEmail
                                + "\",\"password\":\"plain-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(mfaResult.getResponse().getContentAsString()).contains("mfa_required");
        assertThat(projectSessions(mfaUserId)).hasSize(1);
        var pendingSession = projectSessions(mfaUserId).values().iterator().next();
        Object pendingTicket = ((org.springframework.session.Session) pendingSession)
                .getAttribute(NexusSessionAttributes.MFA_PENDING);
        Object securityContext = ((org.springframework.session.Session) pendingSession)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(pendingTicket).isInstanceOf(MfaPendingTicket.class);
        assertThat(securityContext).isNull();
    }

    private RedisIndexedSessionRepository.RedisSession projectUserSession(UUID userId) {
        var session = sessionRepository.createSession();
        session.setAttribute(NexusSessionAttributes.PROJECT_USER_ID, userId.toString());
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                NexusSessionAttributes.projectUserIndexValue(userId));
        session.setAttribute(NexusSessionAttributes.SESSION_PUBLIC_ID, UUID.randomUUID().toString());
        return session;
    }

    private RedisIndexedSessionRepository.RedisSession authenticatedProjectUserSession(
            UUID projectId, UUID userId
    ) {
        var session = projectUserSession(userId);
        var principal = new ProjectUserPrincipal(
                projectId, userId, "session-user", null, List.of(), true, 0L);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private Map<String, ?> projectSessions(UUID userId) {
        return sessionRepository.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                NexusSessionAttributes.projectUserIndexValue(userId));
    }

    private void seedProjectAndUser(UUID projectId, UUID userId, String prefix) {
        seedProjectAndUser(projectId, userId, prefix + "-" + projectId,
                userId + "@session.test", "noop");
    }

    private void seedProjectAndUser(
            UUID projectId, UUID userId, String slug, String email, String passwordHash
    ) {
        jdbc.update("INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Session archive IT', 'ACTIVE', now(), now())",
                projectId, slug);
        jdbc.update("INSERT INTO project_users "
                        + "(id, project_id, email, password_hash, display_name, status, email_verified_at, "
                        + "authz_version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'Session User', 'ACTIVE', now(), 0, now(), now())",
                userId, projectId, email, passwordHash);
    }

    private Cookie csrfCookie(String slug) throws Exception {
        Cookie cookie = mockMvc.perform(get("/api/p/" + slug + "/csrf"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String projectStatus(UUID projectId) {
        return jdbc.queryForObject("SELECT status FROM projects WHERE id = ?", String.class, projectId);
    }
}
