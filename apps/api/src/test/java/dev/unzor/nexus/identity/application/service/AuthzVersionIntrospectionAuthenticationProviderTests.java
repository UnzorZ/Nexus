package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the authz_version introspection enforcement (#22):
 * a stale token introspects as inactive; a current one stays active; a deleted
 * user's token is inactive; non-end-user (client-credentials) tokens are passed
 * through untouched; an already-inactive delegated result is not re-evaluated.
 */
class AuthzVersionIntrospectionAuthenticationProviderTests {

    private static final String TOKEN = "access-token-xyz";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
    private final ProjectUserRepository projectUserRepository = mock(ProjectUserRepository.class);
    private final AuthenticationProvider delegate = mock(AuthenticationProvider.class);
    private final AuthzVersionIntrospectionAuthenticationProvider provider =
            new AuthzVersionIntrospectionAuthenticationProvider(
                    delegate, authorizationService, projectUserRepository);

    private final Authentication clientPrincipal = mock(Authentication.class);
    private final Authentication request = new OAuth2TokenIntrospectionAuthenticationToken(
            TOKEN, clientPrincipal, null, null);

    @BeforeEach
    void setUp() {
        // By default the delegate resolves an ACTIVE token; individual tests override.
        when(delegate.authenticate(request)).thenReturn(activeResult());
    }

    @Test
    void staleAuthzVersionIsReportedInactive() {
        stubAuthorization(userPrincipal(1));            // token minted at version 1
        when(projectUserRepository.findAuthzVersionByProjectIdAndId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(2L));            // current is 2 -> stale

        boolean active = introspection().isActive();

        assertThat(active).isFalse();
    }

    @Test
    void currentAuthzVersionStaysActive() {
        stubAuthorization(userPrincipal(2));
        when(projectUserRepository.findAuthzVersionByProjectIdAndId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(2L));

        assertThat(introspection().isActive()).isTrue();
    }

    @Test
    void deletedUserTokenIsInactive() {
        stubAuthorization(userPrincipal(2));
        when(projectUserRepository.findAuthzVersionByProjectIdAndId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.empty());           // user gone

        assertThat(introspection().isActive()).isFalse();
    }

    @Test
    void nonEndUserTokenIsPassedThrough() {
        // client-credentials flow stores no ProjectUserPrincipal attribute
        OAuth2Authorization auth = mock(OAuth2Authorization.class);
        when(auth.getAttribute(Principal.class.getName())).thenReturn(null);
        when(authorizationService.findByToken(TOKEN, null)).thenReturn(auth);

        assertThat(introspection().isActive()).isTrue();
        verify(projectUserRepository, never()).findAuthzVersionByProjectIdAndId(any(), any());
    }

    @Test
    void alreadyInactiveResultIsNotReEvaluated() {
        when(delegate.authenticate(request)).thenReturn(inactiveResult());

        assertThat(introspection().isActive()).isFalse();
        verify(authorizationService, never()).findByToken(any(), eq(null));
    }

    // ---- helpers ----

    private OAuth2TokenIntrospection introspection() {
        Authentication result = provider.authenticate(request);
        assertThat(result).isInstanceOf(OAuth2TokenIntrospectionAuthenticationToken.class);
        return ((OAuth2TokenIntrospectionAuthenticationToken) result).getTokenClaims();
    }

    private OAuth2TokenIntrospectionAuthenticationToken activeResult() {
        return new OAuth2TokenIntrospectionAuthenticationToken(
                TOKEN, clientPrincipal, OAuth2TokenIntrospection.builder(true).build());
    }

    private OAuth2TokenIntrospectionAuthenticationToken inactiveResult() {
        return new OAuth2TokenIntrospectionAuthenticationToken(
                TOKEN, clientPrincipal, OAuth2TokenIntrospection.builder(false).build());
    }

    private void stubAuthorization(Authentication userAuth) {
        OAuth2Authorization auth = mock(OAuth2Authorization.class);
        when(auth.getAttribute(Principal.class.getName())).thenReturn(userAuth);
        when(authorizationService.findByToken(TOKEN, null)).thenReturn(auth);
    }

    /** Mimics how SAS persists the resource-owner authentication (principal = our record). */
    private static Authentication userPrincipal(long authzVersion) {
        ProjectUserPrincipal pup = new ProjectUserPrincipal(
                PROJECT_ID, USER_ID, "alice", null,
                List.of(), true, authzVersion);
        return new TestingAuthenticationToken(pup, null);
    }
}
