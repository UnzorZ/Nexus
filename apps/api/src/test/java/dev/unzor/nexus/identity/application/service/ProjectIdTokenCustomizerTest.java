package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.permissions.application.dto.EffectiveAuthorities;
import dev.unzor.nexus.permissions.application.service.EffectiveAuthoritiesService;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del {@link ProjectIdTokenCustomizer}: verifica que los claims
 * {@code permissions} (claves de permiso, comodines verbatim) y {@code roles}
 * (claves de rol) se emiten tanto en el access token como en el ID token, junto a
 * {@code project_id} y {@code authz_version}. Se construye el
 * {@link JwtEncodingContext} a mano (sin contexto Spring ni MockMvc).
 */
class ProjectIdTokenCustomizerTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String ISSUER = "http://localhost:8080/p/shop";

    @Test
    void permissionsAndRolesClaimsEmittedInAccessTokenAndIdToken() {
        ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
        when(slugResolver.resolve("shop")).thenReturn(new ProjectAuthenticationContext(PROJECT_ID, "shop"));
        EffectiveAuthoritiesService authorities = mock(EffectiveAuthoritiesService.class);
        // TreeSet refleja el orden determinista real de EffectiveAuthorities.
        when(authorities.forUser(PROJECT_ID, USER_ID))
                .thenReturn(new EffectiveAuthorities(
                        new TreeSet<>(Set.of("orders.*", "orders.read")),
                        new TreeSet<>(Set.of("admin"))));

        ProjectIdTokenCustomizer customizer = new ProjectIdTokenCustomizer(slugResolver, authorities, users(null));
        ProjectUserPrincipal pup = principal(7L);

        // ACCESS y ID token: el customizer no filtra por tipo, así que permissions y roles
        // aterrizan en ambos encodings.
        List<OAuth2TokenType> tokenTypes = List.of(
                OAuth2TokenType.ACCESS_TOKEN,
                new OAuth2TokenType("id_token"));

        for (OAuth2TokenType tokenType : tokenTypes) {
            Map<String, Object> claims = runCustomizer(customizer, tokenType, pup);

            assertThat(claims).as("claims in %s", tokenType.getValue())
                    .containsEntry("project_id", PROJECT_ID.toString())
                    .containsEntry("authz_version", 7L)
                    // Comodines verbatim (ADR-0003), en el orden canónico del TreeSet.
                    .containsEntry("permissions", List.of("orders.*", "orders.read"))
                    // Claves de rol: un cliente autoriza por rol (p. ej. "es admin").
                    .containsEntry("roles", List.of("admin"));
        }
    }

    @Test
    void userWithoutRolesEmitsEmptyRolesClaim() {
        ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
        when(slugResolver.resolve("shop")).thenReturn(new ProjectAuthenticationContext(PROJECT_ID, "shop"));
        EffectiveAuthoritiesService authorities = mock(EffectiveAuthoritiesService.class);
        when(authorities.forUser(PROJECT_ID, USER_ID))
                .thenReturn(EffectiveAuthorities.empty());

        ProjectIdTokenCustomizer customizer = new ProjectIdTokenCustomizer(slugResolver, authorities, users(null));

        Map<String, Object> claims = runCustomizer(customizer, OAuth2TokenType.ACCESS_TOKEN, principal(7L));

        assertThat(claims).containsEntry("roles", List.of());
    }

    @Test
    void nonProjectPrincipalGetsNoClaims() {
        ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
        when(slugResolver.resolve("shop")).thenReturn(new ProjectAuthenticationContext(PROJECT_ID, "shop"));
        EffectiveAuthoritiesService authorities = mock(EffectiveAuthoritiesService.class);

        ProjectIdTokenCustomizer customizer = new ProjectIdTokenCustomizer(slugResolver, authorities, users(null));
        // Principal que NO es ProjectUserPrincipal (p. ej. client credentials): sin claims de usuario.
        Map<String, Object> claims = runCustomizer(customizer, OAuth2TokenType.ACCESS_TOKEN, "not-a-project-user");

        assertThat(claims).doesNotContainKey("permissions").doesNotContainKey("project_id").doesNotContainKey("roles");
    }

    @Test
    void crossRealmPrincipalAbortsTokenIssuance() {
        // El issuer (realm B) != el proyecto del principal (PROJECT_ID = A): la emisión
        // ABORTA (no degrada). Remediación del hallazgo crítico: antes project_id venía
        // del issuer y permissions del principal, mezclando A y B; y luego sólo se
        // omitían los claims, dejando emitir un token iss=B con sujeto de A. Ahora throw.
        UUID otherRealm = UUID.fromString("00000000-0000-0000-0000-000000000099");
        ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
        when(slugResolver.resolve("shop")).thenReturn(new ProjectAuthenticationContext(otherRealm, "shop"));
        EffectiveAuthoritiesService authorities = mock(EffectiveAuthoritiesService.class);

        ProjectIdTokenCustomizer customizer = new ProjectIdTokenCustomizer(slugResolver, authorities, users(null));
        ProjectUserPrincipal pup = principal(7L); // projectId = PROJECT_ID (A)

        assertThatThrownBy(() -> runCustomizer(customizer, OAuth2TokenType.ACCESS_TOKEN, pup))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(authorities);
    }

    @Test
    void unknownIssuerProjectAddsNoClaims() {
        ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
        when(slugResolver.resolve("shop")).thenThrow(new ProjectNotFoundException("shop"));
        EffectiveAuthoritiesService authorities = mock(EffectiveAuthoritiesService.class);

        ProjectIdTokenCustomizer customizer = new ProjectIdTokenCustomizer(slugResolver, authorities, users(null));
        Map<String, Object> claims = runCustomizer(
                customizer, OAuth2TokenType.ACCESS_TOKEN, principal(7L));

        assertThat(claims).doesNotContainKey("permissions").doesNotContainKey("project_id").doesNotContainKey("roles");
        verifyNoInteractions(authorities);
    }

    @Test
    void inactiveIssuerProjectAbortsWithNativeOauthError() {
        ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
        when(slugResolver.resolve("shop")).thenThrow(
                new ProjectNotOperationalException(PROJECT_ID, ProjectStatus.ARCHIVED));
        EffectiveAuthoritiesService authorities = mock(EffectiveAuthoritiesService.class);

        ProjectIdTokenCustomizer customizer = new ProjectIdTokenCustomizer(slugResolver, authorities, users(null));

        assertThatThrownBy(() -> runCustomizer(
                customizer, OAuth2TokenType.ACCESS_TOKEN, principal(7L)))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        error -> assertThat(error.getError().getErrorCode())
                                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
        verifyNoInteractions(authorities);
    }

    @Test
    void passwordOnlySessionEmitsPwdAmrAndPwdAcr() {
        Map<String, Object> claims = runWithFactors(factor(FactorGrantedAuthority.PASSWORD_AUTHORITY));

        assertThat(claims).containsEntry("amr", List.of("pwd")).containsEntry("acr", "pwd");
    }

    @Test
    void passwordAndTotpSessionEmitsOtpAmrAndMfaAcr() {
        Map<String, Object> claims = runWithFactors(
                factor(FactorGrantedAuthority.PASSWORD_AUTHORITY),
                factor("FACTOR_TOTP"));

        assertThat(claims).containsEntry("amr", List.of("pwd", "otp")).containsEntry("acr", "mfa");
    }

    @Test
    void googleAndTotpSessionEmitsGoogleOtpAmrAndMfaAcr() {
        // withFactor("google") genera la authority "FACTOR_google" (sin mayúsculas): el
        // mapeo debe basarse en lo que el session authenticator construye de verdad.
        Map<String, Object> claims = runWithFactors(factor("FACTOR_google"), factor("FACTOR_TOTP"));

        assertThat(claims).containsEntry("amr", List.of("google", "otp")).containsEntry("acr", "mfa");
    }

    @Test
    void googleOnlySessionEmitsGoogleAmrAndGoogleAcr() {
        Map<String, Object> claims = runWithFactors(factor("FACTOR_google"));

        assertThat(claims).containsEntry("amr", List.of("google")).containsEntry("acr", "google");
    }

    @Test
    void sessionWithoutFactorAuthoritiesEmitsNoAmrOrAcr() {
        // Client credentials: la Authentication existe pero sin factores; no se fabrican
        // amr/acr.
        Map<String, Object> claims = runWithFactors();

        assertThat(claims).doesNotContainKey("amr").doesNotContainKey("acr");
    }

    // --- claims estándar email / profile --------------------------------------------
    // Modos de fallo: (1) con scope email el ID token no lleva email, y la app cliente
    // guarda la cuenta sin email (FlipDaFile: soporte, invitaciones); (2) email_verified
    // falta o miente, y la app admite o rechaza por error; (3) se emiten sin que el
    // cliente pidiera el scope (fuga de datos personales); (4) el usuario ya no existe
    // al emitir y el login cae con una excepción; (5) el access token lleva datos
    // personales que ningún resource server necesita.

    @Test
    void emailScopeAddsEmailAndEmailVerifiedToTheIdToken() {
        ProjectUser user = user("alice@example.com", "Alice Doe", "alice");
        user.verifyEmail(java.time.Instant.parse("2026-01-01T00:00:00Z"));

        Map<String, Object> claims = runStandardClaims(user, ID_TOKEN, Set.of("openid", "email"));

        assertThat(claims).containsEntry("email", "alice@example.com").containsEntry("email_verified", true)
                .doesNotContainKey("name").doesNotContainKey("preferred_username");
    }

    @Test
    void unverifiedEmailIsReportedAsNotVerified() {
        ProjectUser user = user("bob@example.com", "Bob", null);

        Map<String, Object> claims = runStandardClaims(user, ID_TOKEN, Set.of("openid", "email"));

        assertThat(claims).containsEntry("email", "bob@example.com").containsEntry("email_verified", false);
    }

    @Test
    void profileScopeAddsNameAndPreferredUsername() {
        ProjectUser user = user("alice@example.com", "Alice Doe", "alice");

        Map<String, Object> claims = runStandardClaims(user, ID_TOKEN, Set.of("openid", "profile"));

        assertThat(claims).containsEntry("name", "Alice Doe").containsEntry("preferred_username", "alice")
                .doesNotContainKey("email").doesNotContainKey("email_verified");
    }

    @Test
    void withoutTheScopesNoPersonalClaimsAreAdded() {
        ProjectUser user = user("alice@example.com", "Alice Doe", "alice");

        Map<String, Object> claims = runStandardClaims(user, ID_TOKEN, Set.of("openid"));

        assertThat(claims).doesNotContainKeys("email", "email_verified", "name", "preferred_username");
    }

    @Test
    void accessTokenNeverCarriesPersonalClaims() {
        ProjectUser user = user("alice@example.com", "Alice Doe", "alice");

        Map<String, Object> claims = runStandardClaims(user, OAuth2TokenType.ACCESS_TOKEN,
                Set.of("openid", "email", "profile"));

        assertThat(claims).doesNotContainKeys("email", "email_verified", "name", "preferred_username")
                .containsKey("project_id");
    }

    @Test
    void aMissingUserSkipsTheClaimsWithoutFailingTheLogin() {
        Map<String, Object> claims = runStandardClaims(null, ID_TOKEN, Set.of("openid", "email", "profile"));

        assertThat(claims).doesNotContainKeys("email", "name").containsKey("project_id");
    }

    private static final OAuth2TokenType ID_TOKEN = new OAuth2TokenType("id_token");

    private static ProjectUser user(String email, String displayName, String username) {
        ProjectUser user = new ProjectUser(PROJECT_ID, email, "hash", displayName);
        if (username != null) {
            user.updateProfile(displayName, username);
        }
        return user;
    }

    private static ProjectUserRepository users(ProjectUser user) {
        ProjectUserRepository repository = mock(ProjectUserRepository.class);
        when(repository.findByProjectIdAndId(PROJECT_ID, USER_ID)).thenReturn(java.util.Optional.ofNullable(user));
        return repository;
    }

    private static Map<String, Object> runStandardClaims(ProjectUser user, OAuth2TokenType tokenType, Set<String> scopes) {
        var auth = new UsernamePasswordAuthenticationToken(principal(7L), null, List.of());
        JwtEncodingContext ctx = JwtEncodingContext
                .with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder())
                .tokenType(tokenType)
                .authorizationServerContext(serverContext())
                .principal(auth)
                .authorizedScopes(scopes)
                .build();
        ctx.getClaims().claim("iss", ISSUER);
        ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
        when(slugResolver.resolve("shop")).thenReturn(new ProjectAuthenticationContext(PROJECT_ID, "shop"));
        EffectiveAuthoritiesService authorities = mock(EffectiveAuthoritiesService.class);
        when(authorities.forUser(PROJECT_ID, USER_ID)).thenReturn(EffectiveAuthorities.empty());
        new ProjectIdTokenCustomizer(slugResolver, authorities, users(user)).customize(ctx);
        return ctx.getClaims().build().getClaims();
    }

    private static FactorGrantedAuthority factor(String authority) {
        return FactorGrantedAuthority.fromAuthority(authority);
    }

    /** Igual que {@link #runCustomizer}, pero la Authentication lleva factores dados. */
    private static Map<String, Object> runWithFactors(GrantedAuthority... factors) {
        var auth = new UsernamePasswordAuthenticationToken(principal(7L), null, List.of(factors));
        JwtEncodingContext ctx = JwtEncodingContext
                .with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder())
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .authorizationServerContext(serverContext())
                .principal(auth)
                .build();
        ctx.getClaims().claim("iss", ISSUER);
        ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
        when(slugResolver.resolve("shop")).thenReturn(new ProjectAuthenticationContext(PROJECT_ID, "shop"));
        EffectiveAuthoritiesService authorities = mock(EffectiveAuthoritiesService.class);
        when(authorities.forUser(PROJECT_ID, USER_ID)).thenReturn(EffectiveAuthorities.empty());
        new ProjectIdTokenCustomizer(slugResolver, authorities, users(null)).customize(ctx);
        return ctx.getClaims().build().getClaims();
    }

    private static ProjectUserPrincipal principal(long authzVersion) {
        return new ProjectUserPrincipal(PROJECT_ID, USER_ID, "alice", "pw", List.of(), true, authzVersion);
    }

    private static Map<String, Object> runCustomizer(
            ProjectIdTokenCustomizer customizer, OAuth2TokenType tokenType, Object principal) {
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        JwtEncodingContext ctx = JwtEncodingContext
                .with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder())
                .tokenType(tokenType)
                .authorizationServerContext(serverContext())
                .principal(auth)
                .build();
        // JwtClaimsSet.Builder.build() rechaza un claim set vacío; sembramos un claim
        // base (irrelevante para el customizer) para que build() siempre funcione.
        ctx.getClaims().claim("iss", ISSUER);
        customizer.customize(ctx);
        return ctx.getClaims().build().getClaims();
    }

    /** {@link AuthorizationServerContext} mínimo: sólo expone el issuer por proyecto. */
    private static AuthorizationServerContext serverContext() {
        return new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return ISSUER;
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return AuthorizationServerSettings.builder().multipleIssuersAllowed(true).build();
            }
        };
    }
}
