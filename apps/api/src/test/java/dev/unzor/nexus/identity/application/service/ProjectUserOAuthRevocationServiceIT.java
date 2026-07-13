package dev.unzor.nexus.identity.application.service;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import dev.unzor.nexus.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * IT contra PostgreSQL real del {@link ProjectUserOAuthRevocationService}: valida que
 * el DELETE cross-type ({@code oauth2_authorization.registered_client_id} VARCHAR vs
 * {@code project_oauth_clients.id} UUID) funciona con el CAST — sin él, PostgreSQL lanza
 * "operator does not exist: character varying = uuid", aborta la transacción y la
 * revocación nunca ocurre (fallo silencioso que un test unitario con JdbcTemplate mockeado
 * no detecta). Valida además el acotado por proyecto.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProjectUserOAuthRevocationServiceIT {

    private static final UUID PROJECT_A = UUID.fromString("00000000-0000-0000-0000-0000000001a1");
    private static final UUID PROJECT_B = UUID.fromString("00000000-0000-0000-0000-0000000001b2");
    private static final UUID CLIENT_A = UUID.randomUUID();
    private static final UUID CLIENT_B = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final String SHARED_PRINCIPAL_NAME = "shared-subject";

    private ProjectUserOAuthRevocationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BackChannelLogoutClientResolver logoutClientResolver;

    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void seed() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ProjectUserOAuthRevocationService(jdbc, logoutClientResolver, eventPublisher);
        jdbc.update("DELETE FROM oauth2_authorization WHERE id IN ('auth-a', 'auth-b', 'auth-other')");
        jdbc.update("DELETE FROM project_oauth_clients WHERE id IN (?, ?)", CLIENT_A, CLIENT_B);
        jdbc.update("DELETE FROM projects WHERE id IN (?, ?)", PROJECT_A, PROJECT_B);
        seedProject(PROJECT_A, "realm-revoke-a-" + UUID.randomUUID());
        seedProject(PROJECT_B, "realm-revoke-b-" + UUID.randomUUID());
        // registered_client_id en oauth2_authorization es el id del cliente como texto.
        seedProjectClient(PROJECT_A, CLIENT_A);
        seedProjectClient(PROJECT_B, CLIENT_B);
        seedAuthorization("auth-a", CLIENT_A.toString(), USER_ID);
        seedAuthorization("auth-b", CLIENT_B.toString(), USER_ID);
        // Otro usuario del mismo proyecto/cliente debe permanecer intacto, incluso si
        // comparte atributos editables como username (la clave persistida es su UUID).
        seedAuthorization("auth-other", CLIENT_A.toString(), OTHER_USER_ID);
    }

    private void seedProject(UUID projectId, String slug) {
        jdbc.update("INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                + "VALUES (?, ?, 'Revoke IT', 'ACTIVE', now(), now())", projectId, slug);
    }

    @Test
    void revokesAuthorizationsForTheProjectOnly() {
        service.revokeForProjectUser(PROJECT_A, USER_ID);

        assertThat(authorizationExists("auth-a")).isFalse(); // usuario + proyecto objetivo
        assertThat(authorizationExists("auth-b")).isTrue(); // mismo usuario, otro proyecto
        assertThat(authorizationExists("auth-other")).isTrue(); // mismo nombre, otro userId

        var eventCaptor = forClass(BackChannelLogoutRequested.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        BackChannelLogoutRequested event = eventCaptor.getValue();
        assertThat(event.principalName()).isEqualTo(SHARED_PRINCIPAL_NAME);
        assertThat(event.projectId()).isEqualTo(PROJECT_A);
        assertThat(event.issuer()).isEqualTo("https://nexus.example/p/project-a");
        assertThat(event.targets())
                .extracting(BackChannelLogoutTarget::id)
                .containsExactly(CLIENT_A);
    }

    @Test
    void isIdempotentForUnknownPrincipal() {
        // Un principal sin autorizaciones no lanza (ni aborta la tx).
        service.revokeForProjectUser(PROJECT_A, UUID.randomUUID());
        assertThat(authorizationExists("auth-a")).isTrue();
        verifyNoInteractions(eventPublisher);
    }

    private void seedProjectClient(UUID projectId, UUID clientId) {
        jdbc.update("INSERT INTO project_oauth_clients "
                        + "(id, project_id, client_id, client_secret_hash, name, redirect_uris, "
                        + "post_logout_redirect_uris, grant_types, scopes, require_pkce, "
                        + "consent_required, backchannel_logout_uri, status, created_by_account_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', NULL, now(), now())",
                clientId, projectId, "client-" + clientId, "Client " + clientId,
                "https://app/cb", "", "authorization_code\nrefresh_token", "openid", false, false,
                "https://app.example/backchannel");
    }

    private void seedAuthorization(String id, String registeredClientId, UUID userId) {
        jdbc.update("INSERT INTO oauth2_authorization "
                        + "(id, registered_client_id, principal_name, authorization_grant_type, attributes, "
                        + "oidc_id_token_value) "
                        + "VALUES (?, ?, ?, 'refresh_token', CAST(? AS TEXT), ?)",
                id, registeredClientId, SHARED_PRINCIPAL_NAME,
                "{\"authentication\":{\"principal\":{\"userId\":\"" + userId + "\"}}}",
                idTokenFor(registeredClientId));
    }

    private static String idTokenFor(String registeredClientId) {
        String realm = CLIENT_A.toString().equals(registeredClientId) ? "project-a" : "project-b";
        return new PlainJWT(new JWTClaimsSet.Builder()
                .issuer("https://nexus.example/p/" + realm)
                .subject(SHARED_PRINCIPAL_NAME)
                .build()).serialize();
    }

    private boolean authorizationExists(String authorizationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization WHERE id = ?",
                Integer.class, authorizationId);
        return count != null && count > 0;
    }
}
