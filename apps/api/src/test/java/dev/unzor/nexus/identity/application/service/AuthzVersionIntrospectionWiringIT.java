package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.identity.application.configuration.NexusOAuthBootstrapProperties;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring guard for {@link AuthzVersionIntrospectionAuthenticationProvider}: exercises
 * the provider with the REAL JDBC {@link OAuth2AuthorizationService} + the REAL
 * {@link ProjectUserRepository} projection (not mocks). Proves the persisted
 * {@code Principal.class.getName()} attribute round-trips through {@code oauth2_authorization}
 * and that a token goes {@code active:true -> active:false} once the user's
 * {@code authz_version} passes the value stamped on the token.
 *
 * <p>It builds the {@link OAuth2Authorization} directly (no authorize/consent dance),
 * mirroring {@code OAuthPersistenceIntegrationTests#projectUserPrincipalAndNumericClaimRoundTripThroughJdbc},
 * and wraps the real default introspection provider exactly as
 * {@code SecurityConfig} does in production.</p>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuthzVersionIntrospectionWiringIT {

    @Autowired private OAuth2AuthorizationService authorizationService;
    @Autowired private RegisteredClientRepository registeredClientRepository;
    @Autowired private ProjectUserRepository projectUserRepository;
    @Autowired private NexusOAuthBootstrapProperties bootstrapProperties;
    @Autowired private JdbcTemplate jdbcTemplate;

    private AuthzVersionIntrospectionAuthenticationProvider provider;
    private RegisteredClient client;

    @BeforeEach
    void setUp() {
        client = registeredClientRepository.findByClientId(bootstrapProperties.clientId());
        AuthenticationProvider delegate = new OAuth2TokenIntrospectionAuthenticationProvider(
                registeredClientRepository, authorizationService);
        provider = new AuthzVersionIntrospectionAuthenticationProvider(
                delegate, authorizationService, projectUserRepository);
    }

    @Test
    void staleAuthzVersionIntrospectsInactiveWithRealBeans() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        seedProjectAndUser(projectId, userId, 0L);            // user starts at version 0

        String token = "wiring-stale-" + UUID.randomUUID();
        saveAuthorization(token, projectId, userId, 0L);      // token minted at version 0

        assertThat(introspect(token).isActive()).as("current==token version -> active").isTrue();

        bumpAuthzVersion(userId, 1L);                         // role change -> version 1
        assertThat(introspect(token).isActive()).as("token version < current -> inactive").isFalse();
    }

    @Test
    void deletedUserTokenIntrospectsInactive() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        seedProjectAndUser(projectId, userId, 5L);
        String token = "wiring-del-" + UUID.randomUUID();
        saveAuthorization(token, projectId, userId, 5L);

        assertThat(introspect(token).isActive()).isTrue();

        jdbcTemplate.update("DELETE FROM project_users WHERE id = ?", userId);
        assertThat(introspect(token).isActive()).as("user gone -> inactive").isFalse();
    }

    // ---- helpers ----

    private OAuth2TokenIntrospection introspect(String token) {
        OAuth2ClientAuthenticationToken clientPrincipal = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, client.getClientSecret());
        Authentication request = new OAuth2TokenIntrospectionAuthenticationToken(
                token, clientPrincipal, null, null);
        Authentication result = provider.authenticate(request);
        assertThat(result).isInstanceOf(OAuth2TokenIntrospectionAuthenticationToken.class);
        return ((OAuth2TokenIntrospectionAuthenticationToken) result).getTokenClaims();
    }

    private void saveAuthorization(String tokenValue, UUID projectId, UUID userId, long tokenAuthzVersion) {
        ProjectUserPrincipal principal = new ProjectUserPrincipal(
                projectId, userId, "alice", null,
                List.of(new SimpleGrantedAuthority("ROLE_PROJECT_USER")), true, tokenAuthzVersion);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, tokenValue,
                Instant.now(), Instant.now().plusSeconds(300), Set.of(OidcScopes.OPENID));
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id("wiring-auth-" + tokenValue)
                .principalName("alice")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(Principal.class.getName(), authentication)
                .token(accessToken)
                .build();
        authorizationService.save(authorization);
    }

    private void seedProjectAndUser(UUID projectId, UUID userId, long authzVersion) {
        // postgres JDBC can't infer a bind type for Instant; Timestamp maps to TIMESTAMPTZ.
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                projectId, "wiring-" + projectId, "Wiring IT", now, now);
        jdbcTemplate.update(
                "INSERT INTO project_users (id, project_id, email, password_hash, display_name, status, authz_version, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                userId, projectId, "alice-" + userId + "@wiring.test", "noop",
                "Alice", authzVersion, now, now);
    }

    private void bumpAuthzVersion(UUID userId, long version) {
        jdbcTemplate.update("UPDATE project_users SET authz_version = ? WHERE id = ?", version, userId);
    }
}
