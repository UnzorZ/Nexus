package dev.unzor.nexus.identity.persistence;

import dev.unzor.nexus.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.sql.Timestamp;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProjectOperationalOAuth2AuthorizationServiceIT {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-00000000a101");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-00000000a102");
    private static final String CLIENT_IDENTIFIER = "m3-hydration-client";
    private static final String AUTHORIZATION_ID = "m3-hydration-authorization";
    private static final String ACCESS_TOKEN = "m3-hydration-access-token";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RegisteredClientRepository registeredClients;

    @Autowired
    private OAuth2AuthorizationService authorizations;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM oauth2_authorization WHERE id = ?", AUTHORIZATION_ID);
        jdbc.update("DELETE FROM project_oauth_clients WHERE id = ?", CLIENT_ID);
        jdbc.update("DELETE FROM projects WHERE id = ?", PROJECT_ID);
    }

    @Test
    void inactiveProjectAuthorizationHydratesThenReturnsMissingInsteadOfThrowing() {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        jdbc.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                        + "VALUES (?, 'm3-hydration', 'M3 Hydration', 'ACTIVE', ?, ?)",
                PROJECT_ID, timestamp, timestamp);
        jdbc.update("INSERT INTO project_oauth_clients "
                        + "(id, project_id, client_id, client_secret_hash, name, redirect_uris, "
                        + "post_logout_redirect_uris, grant_types, scopes, require_pkce, "
                        + "consent_required, status, created_by_account_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, 'M3 client', 'https://app/cb', '', "
                        + "'authorization_code', 'openid', false, false, 'ACTIVE', NULL, ?, ?)",
                CLIENT_ID, PROJECT_ID, CLIENT_IDENTIFIER, timestamp, timestamp);

        RegisteredClient client = registeredClients.findByClientId(CLIENT_IDENTIFIER);
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                ACCESS_TOKEN,
                now,
                now.plusSeconds(300),
                Set.of("openid"));
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(AUTHORIZATION_ID)
                .principalName("project-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(token)
                .build();
        authorizations.save(authorization);

        jdbc.update("UPDATE projects SET status = 'ARCHIVED', updated_at = now() WHERE id = ?", PROJECT_ID);

        assertThat(authorizations.findByToken(ACCESS_TOKEN, OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization WHERE id = ?", Integer.class, AUTHORIZATION_ID))
                .isEqualTo(1);
    }

    @Test
    void databaseBarrierRejectsDirectAuthorizationWriteForInactiveProject() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                        + "VALUES (?, 'm3-hydration', 'M3 Hydration', 'SUSPENDED', ?, ?)",
                PROJECT_ID, now, now);
        jdbc.update("INSERT INTO project_oauth_clients "
                        + "(id, project_id, client_id, client_secret_hash, name, redirect_uris, "
                        + "post_logout_redirect_uris, grant_types, scopes, require_pkce, "
                        + "consent_required, status, created_by_account_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, 'M3 client', 'https://app/cb', '', "
                        + "'authorization_code', 'openid', false, false, 'ACTIVE', NULL, ?, ?)",
                CLIENT_ID, PROJECT_ID, CLIENT_IDENTIFIER, now, now);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO oauth2_authorization "
                        + "(id, registered_client_id, principal_name, authorization_grant_type) "
                        + "VALUES ('m3-direct-write', ?, 'project-user', 'refresh_token')",
                CLIENT_ID.toString()))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, error -> {
                    assertThat(error.getRootCause()).isInstanceOfSatisfying(SQLException.class, sqlError ->
                            assertThat(sqlError.getSQLState()).isEqualTo("23514"));
                });
        assertThat(jdbc.queryForObject(
                "SELECT pg_get_functiondef('enforce_operational_project_oauth_authorization'::regproc)",
                String.class)).contains("ck_oauth_authorization_operational_project");
    }
}
