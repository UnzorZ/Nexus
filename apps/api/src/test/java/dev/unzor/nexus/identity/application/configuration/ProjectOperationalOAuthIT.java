package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** Exercises the M3 operational gate through the real Authorization Server chain. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectOperationalOAuthIT {

    private static final String CLIENT_SECRET = "m3-project-secret";
    private static final String REDIRECT_URI = "https://project.example/callback";
    private static final String REFRESH_TOKEN = "m3-persisted-refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RegisteredClientRepository registeredClients;

    @Autowired
    private OAuth2AuthorizationService authorizations;

    @Autowired
    private NexusOAuthBootstrapProperties bootstrapProperties;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> projectIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID projectId : projectIds) {
            jdbc.update("DELETE FROM oauth2_authorization WHERE registered_client_id IN "
                    + "(SELECT CAST(id AS TEXT) FROM project_oauth_clients WHERE project_id = ?)", projectId);
            jdbc.update("DELETE FROM project_oauth_clients WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM projects WHERE id = ?", projectId);
        }
    }

    @Test
    void activeProjectClientCompletesRealClientCredentialsFlow() throws Exception {
        SeededClient client = seedClient(ProjectStatus.ACTIVE, "client_credentials");

        mockMvc.perform(post("/p/" + client.slug + "/oauth2/token")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(client.clientId, CLIENT_SECRET))
                        .param("grant_type", "client_credentials")
                        .param("scope", "profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    void activeRealmStillAllowsDynamicRegistration() throws Exception {
        SeededProject project = seedProject(ProjectStatus.ACTIVE);

        mockMvc.perform(dynamicRegistration(project.slug))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client_id").isNotEmpty());

        assertThat(projectClientCount(project.projectId)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"SUSPENDED", "ARCHIVED"})
    void inactiveProjectCannotProgressAuthorizeOrTokenAndRealmDoesNotFallBackGlobal(
            ProjectStatus status
    ) throws Exception {
        SeededClient client = seedClient(status, "authorization_code\nclient_credentials\nrefresh_token");

        mockMvc.perform(get("/p/" + client.slug + "/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", client.clientId)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("scope", "openid profile")
                        .param("state", "m3-inactive")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest())
                // SAS deliberately emits no body when client_id itself is invalid,
                // avoiding a redirect to an untrusted URI.
                .andExpect(content().string(""));

        mockMvc.perform(post("/p/" + client.slug + "/oauth2/token")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(client.clientId, CLIENT_SECRET))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));

        // The global token path also exposes project clients through the composite;
        // lifecycle gating must apply there as well.
        mockMvc.perform(post("/oauth2/token")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(client.clientId, CLIENT_SECRET))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));

        // A realm path must never resolve the technical bootstrap client from the
        // global repository. If fallback occurred this request would authenticate and
        // fail later as unauthorized_grant, rather than invalid_client.
        mockMvc.perform(post("/p/" + client.slug + "/oauth2/token")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(
                                bootstrapProperties.clientId(), bootstrapProperties.clientSecret()))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"SUSPENDED", "ARCHIVED"})
    void persistedRefreshCannotMintAccessTokenAfterProjectBecomesInactive(
            ProjectStatus status
    ) throws Exception {
        SeededClient client = seedClient(ProjectStatus.ACTIVE, "authorization_code\nrefresh_token");
        RegisteredClient registeredClient = registeredClients.findByClientId(client.clientId);
        Instant now = Instant.now();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id("m3-refresh-" + client.clientDatabaseId)
                .principalName("m3-project-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new OAuth2RefreshToken(REFRESH_TOKEN + "-" + client.clientDatabaseId,
                        now, now.plusSeconds(600)))
                .build();
        authorizations.save(authorization);
        jdbc.update("UPDATE projects SET status = ?, updated_at = now() WHERE id = ?",
                status.name(), client.projectId);

        mockMvc.perform(post("/p/" + client.slug + "/oauth2/token")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(client.clientId, CLIENT_SECRET))
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", REFRESH_TOKEN + "-" + client.clientDatabaseId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"))
                .andExpect(jsonPath("$", not(hasKey("access_token"))));

        assertThat(jdbc.queryForObject(
                "SELECT access_token_value FROM oauth2_authorization WHERE id = ?",
                String.class, authorization.getId())).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"SUSPENDED", "ARCHIVED"})
    void inactiveRealmRejectsDynamicRegistrationWithNativeOauthError(ProjectStatus status) throws Exception {
        SeededProject project = seedProject(status);

        mockMvc.perform(dynamicRegistration(project.slug))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        assertThat(projectClientCount(project.projectId)).isZero();
    }

    @Test
    void dcrWaitingBehindConcurrentArchiveCannotPersistClient() throws Exception {
        SeededProject project = seedProject(ProjectStatus.ACTIVE);
        CountDownLatch archiveWriteHeld = new CountDownLatch(1);
        CountDownLatch allowArchiveCommit = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var archive = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(ignored -> {
                        jdbc.update("UPDATE projects SET status = 'ARCHIVED', updated_at = now() WHERE id = ?",
                                project.projectId);
                        archiveWriteHeld.countDown();
                        await(allowArchiveCommit);
                    }));
            assertThat(archiveWriteHeld.await(5, TimeUnit.SECONDS)).isTrue();

            var registration = executor.submit(() -> mockMvc.perform(dynamicRegistration(project.slug))
                    .andReturn().getResponse());
            assertThatThrownBy(() -> registration.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowArchiveCommit.countDown();
            archive.get(5, TimeUnit.SECONDS);
            var response = registration.get(5, TimeUnit.SECONDS);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("\"error\":\"invalid_request\"");
            assertThat(projectClientCount(project.projectId)).isZero();
        } finally {
            allowArchiveCommit.countDown();
            executor.shutdownNow();
        }
    }

    private SeededClient seedClient(ProjectStatus status, String grantTypes) {
        SeededProject project = seedProject(status);
        UUID databaseId = UUID.randomUUID();
        String clientId = "nxo-m3-" + databaseId.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        String secretHash = new BCryptPasswordEncoder().encode(CLIENT_SECRET);
        jdbc.update("INSERT INTO project_oauth_clients "
                        + "(id, project_id, client_id, client_secret_hash, name, redirect_uris, "
                        + "post_logout_redirect_uris, grant_types, scopes, require_pkce, consent_required, "
                        + "status, created_by_account_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'M3 Project Client', ?, '', ?, 'openid\nprofile', "
                        + "true, false, 'ACTIVE', NULL, ?, ?)",
                databaseId, project.projectId, clientId, secretHash, REDIRECT_URI,
                grantTypes, now, now);
        return new SeededClient(project.projectId, project.slug, databaseId, clientId);
    }

    private SeededProject seedProject(ProjectStatus status) {
        UUID projectId = UUID.randomUUID();
        projectIds.add(projectId);
        String slug = "m3-oauth-" + projectId.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'M3 OAuth IT', ?, ?, ?)",
                projectId, slug, status.name(), now, now);
        return new SeededProject(projectId, slug);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder dynamicRegistration(
            String slug
    ) {
        return post("/p/" + slug + "/oauth2/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "redirect_uris": ["https://project.example/callback"],
                          "grant_types": ["authorization_code", "refresh_token"],
                          "token_endpoint_auth_method": "client_secret_basic",
                          "client_name": "M3 project client"
                        }
                        """);
    }

    private int projectClientCount(UUID projectId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_oauth_clients WHERE project_id = ?",
                Integer.class, projectId);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent DCR test");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during concurrent DCR test", interrupted);
        }
    }

    private record SeededProject(UUID projectId, String slug) {
    }

    private record SeededClient(UUID projectId, String slug, UUID clientDatabaseId, String clientId) {
    }
}
