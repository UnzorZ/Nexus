package dev.unzor.nexus.identity.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifica V53 contra una base PostgreSQL aislada partiendo exactamente del
 * esquema V52, incluido el saneamiento de grants históricos y su trigger de
 * integridad para escrituras posteriores.
 */
@Testcontainers
class V53ArchivedProjectOAuthMigrationIT {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:latest"));

    private static final UUID ACTIVE_PROJECT =
            UUID.fromString("00000000-0000-0000-0000-0000000053a1");
    private static final UUID SUSPENDED_PROJECT =
            UUID.fromString("00000000-0000-0000-0000-0000000053b2");
    private static final UUID ARCHIVED_PROJECT =
            UUID.fromString("00000000-0000-0000-0000-0000000053c3");
    private static final UUID ACTIVE_CLIENT =
            UUID.fromString("00000000-0000-0000-0000-0000000054a1");
    private static final UUID SUSPENDED_CLIENT =
            UUID.fromString("00000000-0000-0000-0000-0000000054b2");
    private static final UUID ARCHIVED_CLIENT =
            UUID.fromString("00000000-0000-0000-0000-0000000054c3");
    private static final UUID GLOBAL_UUID_SHAPED_CLIENT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000054d4");

    @Test
    void removesOnlyArchivedProjectGrantsAndEnforcesOperationalWrites() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("52"))
                .load()
                .migrate();

        JdbcTemplate jdbc = jdbcTemplate();
        seedProject(jdbc, ACTIVE_PROJECT, "v53-active", "ACTIVE");
        seedProject(jdbc, SUSPENDED_PROJECT, "v53-suspended", "SUSPENDED");
        seedProject(jdbc, ARCHIVED_PROJECT, "v53-archived", "ARCHIVED");
        seedClient(jdbc, ACTIVE_PROJECT, ACTIVE_CLIENT, "v53-active-client");
        seedClient(jdbc, SUSPENDED_PROJECT, SUSPENDED_CLIENT, "v53-suspended-client");
        seedClient(jdbc, ARCHIVED_PROJECT, ARCHIVED_CLIENT, "v53-archived-client");
        seedAuthorization(jdbc, "auth-active", ACTIVE_CLIENT.toString());
        seedAuthorization(jdbc, "auth-suspended", SUSPENDED_CLIENT.toString());
        seedAuthorization(jdbc, "auth-archived", ARCHIVED_CLIENT.toString());
        seedAuthorization(jdbc, "auth-global-non-uuid", "global-client-without-project");
        seedAuthorization(jdbc, "auth-global-uuid", GLOBAL_UUID_SHAPED_CLIENT_ID.toString());

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(authorizationIds(jdbc))
                .containsExactlyInAnyOrder(
                        "auth-active",
                        "auth-suspended",
                        "auth-global-non-uuid",
                        "auth-global-uuid")
                .doesNotContain("auth-archived");

        assertThatCode(() -> seedAuthorization(jdbc, "after-active", ACTIVE_CLIENT.toString()))
                .doesNotThrowAnyException();
        assertThatCode(() -> seedAuthorization(jdbc, "after-global-non-uuid", "another-global-client"))
                .doesNotThrowAnyException();
        assertThatCode(() -> seedAuthorization(
                jdbc,
                "after-global-uuid",
                GLOBAL_UUID_SHAPED_CLIENT_ID.toString()))
                .doesNotThrowAnyException();
        assertRejectedByOperationalTrigger(jdbc, "after-suspended", SUSPENDED_CLIENT);
        assertRejectedByOperationalTrigger(jdbc, "after-archived", ARCHIVED_CLIENT);

        String triggerFunction = jdbc.queryForObject(
                "SELECT pg_get_functiondef('enforce_operational_project_oauth_authorization()'::regprocedure)",
                String.class);
        assertThat(triggerFunction)
                .containsPattern("(?i)c\\.id\\s*=\\s*project_client_id")
                .doesNotContainPattern(
                        "(?i)(?:cast\\s*\\(\\s*c\\.id\\s+as\\s+text\\s*\\)|\\(?c\\.id\\)?\\s*::\\s*text)");
    }

    private JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private static void seedProject(JdbcTemplate jdbc, UUID projectId, String slug, String status) {
        jdbc.update("INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'V53 migration IT', ?, now(), now())",
                projectId, slug, status);
    }

    private static void seedClient(JdbcTemplate jdbc, UUID projectId, UUID clientId, String clientIdValue) {
        jdbc.update("INSERT INTO project_oauth_clients "
                        + "(id, project_id, client_id, client_secret_hash, name, redirect_uris, "
                        + "post_logout_redirect_uris, grant_types, scopes, require_pkce, "
                        + "consent_required, backchannel_logout_uri, status, created_by_account_id, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, 'V53 client', 'https://app.example/callback', '', "
                        + "'authorization_code\nrefresh_token', 'openid', false, false, NULL, "
                        + "'ACTIVE', NULL, now(), now())",
                clientId, projectId, clientIdValue);
    }

    private static void seedAuthorization(JdbcTemplate jdbc, String authorizationId, String registeredClientId) {
        jdbc.update("INSERT INTO oauth2_authorization "
                        + "(id, registered_client_id, principal_name, authorization_grant_type) "
                        + "VALUES (?, ?, 'v53-subject', 'refresh_token')",
                authorizationId, registeredClientId);
    }

    private static String[] authorizationIds(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                        "SELECT id FROM oauth2_authorization ORDER BY id", String.class)
                .toArray(String[]::new);
    }

    private static void assertRejectedByOperationalTrigger(
            JdbcTemplate jdbc,
            String authorizationId,
            UUID clientId
    ) {
        Throwable failure = catchThrowable(
                () -> seedAuthorization(jdbc, authorizationId, clientId.toString()));
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rootCause(failure)).isInstanceOfSatisfying(SQLException.class, sqlException -> {
            assertThat(sqlException.getSQLState()).isEqualTo("23514");
            assertThat(sqlException.getMessage())
                    .contains("Cannot persist OAuth authorization for a non-operational project");
        });
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
