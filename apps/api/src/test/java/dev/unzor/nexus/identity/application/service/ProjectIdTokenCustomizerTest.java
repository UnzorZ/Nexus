package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.permissions.application.dto.EffectiveAuthorities;
import dev.unzor.nexus.permissions.application.service.EffectiveAuthoritiesService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
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
 * Tests unitarios del {@link ProjectIdTokenCustomizer}: verifica que el claim
 * {@code permissions} (claves de permiso, comodines verbatim) se emite tanto en
 * el access token como en el ID token, junto a {@code project_id} y
 * {@code authz_version}. Se construye el {@link JwtEncodingContext} a mano (sin
 * contexto Spring ni MockMvc).
 */
class ProjectIdTokenCustomizerTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String ISSUER = "http://localhost:8080/p/shop";

    @Test
    void permissionsClaimEmittedInAccessTokenAndIdToken() {
        ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
        when(slugResolver.resolve("shop")).thenReturn(new ProjectAuthenticationContext(PROJECT_ID, "shop"));
        EffectiveAuthoritiesService authorities = mock(EffectiveAuthoritiesService.class);
        // TreeSet refleja el orden determinista real de EffectiveAuthorities.
        when(authorities.forUser(PROJECT_ID, USER_ID))
                .thenReturn(new EffectiveAuthorities(new TreeSet<>(Set.of("orders.*", "orders.read"))));

        ProjectIdTokenCustomizer customizer = new ProjectIdTokenCustomizer(slugResolver, authorities);
        ProjectUserPrincipal pup = principal(7L);

        // ACCESS y ID token: el customizer no filtra por tipo, así que permissions
        // aterriza en ambos encodings.
        List<OAuth2TokenType> tokenTypes = List.of(
                OAuth2TokenType.ACCESS_TOKEN,
                new OAuth2TokenType("id_token"));

        for (OAuth2TokenType tokenType : tokenTypes) {
            Map<String, Object> claims = runCustomizer(customizer, tokenType, pup);

            assertThat(claims).as("claims in %s", tokenType.getValue())
                    .containsEntry("project_id", PROJECT_ID.toString())
                    .containsEntry("authz_version", 7L)
                    // Comodines verbatim (ADR-0003), en el orden canónico del TreeSet.
                    .containsEntry("permissions", List.of("orders.*", "orders.read"));
        }
    }

    @Test
    void nonProjectPrincipalGetsNoClaims() {
        ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
        when(slugResolver.resolve("shop")).thenReturn(new ProjectAuthenticationContext(PROJECT_ID, "shop"));
        EffectiveAuthoritiesService authorities = mock(EffectiveAuthoritiesService.class);

        ProjectIdTokenCustomizer customizer = new ProjectIdTokenCustomizer(slugResolver, authorities);
        // Principal que NO es ProjectUserPrincipal (p. ej. client credentials): sin claims de usuario.
        Map<String, Object> claims = runCustomizer(customizer, OAuth2TokenType.ACCESS_TOKEN, "not-a-project-user");

        assertThat(claims).doesNotContainKey("permissions").doesNotContainKey("project_id");
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

        ProjectIdTokenCustomizer customizer = new ProjectIdTokenCustomizer(slugResolver, authorities);
        ProjectUserPrincipal pup = principal(7L); // projectId = PROJECT_ID (A)

        assertThatThrownBy(() -> runCustomizer(customizer, OAuth2TokenType.ACCESS_TOKEN, pup))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(authorities);
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
