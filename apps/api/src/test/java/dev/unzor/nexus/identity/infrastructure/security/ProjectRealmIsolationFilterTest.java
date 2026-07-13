package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del {@link ProjectRealmIsolationFilter}: una sesión de otro realm
 * no puede alcanzar los endpoints de este slug (ni la API JSON ni el authorize OAuth).
 * El filtro es el punto único de estrangulamiento, así cubre /me, MFA, sesiones y la
 * emisión de tokens sin tocar cada handler.
 */
class ProjectRealmIsolationFilterTest {

    private static final UUID REALM_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REALM_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final ProjectSlugResolver slugResolver = mock(ProjectSlugResolver.class);
    private final ProjectRealmIsolationFilter filter = new ProjectRealmIsolationFilter(slugResolver);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksCrossRealmPrincipalOnEndUserApi() throws Exception {
        when(slugResolver.resolve("realm-b")).thenReturn(new ProjectAuthenticationContext(REALM_B, "realm-b"));
        authenticate(REALM_A); // sesión abierta en el realm A

        MockHttpServletResponse response = run("GET", "/api/p/realm-b/me");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void blocksCrossRealmPrincipalOnOauthAuthorize() throws Exception {
        when(slugResolver.resolve("realm-b")).thenReturn(new ProjectAuthenticationContext(REALM_B, "realm-b"));
        authenticate(REALM_A);

        MockHttpServletResponse response = run("GET", "/p/realm-b/oauth2/authorize");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void allowsMatchingRealm() throws Exception {
        when(slugResolver.resolve("realm-a")).thenReturn(new ProjectAuthenticationContext(REALM_A, "realm-a"));
        authenticate(REALM_A);

        MockHttpServletResponse response = run("GET", "/api/p/realm-a/me");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowsWhenNoPrincipal() throws Exception {
        // permitAll (login, register…): sin principal → pasa sin tocar el gate.
        when(slugResolver.resolve("realm-a")).thenReturn(new ProjectAuthenticationContext(REALM_A, "realm-a"));

        MockHttpServletResponse response = run("POST", "/api/p/realm-a/login");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowsWhenSlugUnresolvable() throws Exception {
        // Slug que no es proyecto: no se rechaza aquí (el handler devolverá su 404).
        when(slugResolver.resolve("nope")).thenThrow(new RuntimeException("not found"));
        authenticate(REALM_A);

        MockHttpServletResponse response = run("GET", "/api/p/nope/me");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresPathsOutsideProjectRealms() throws Exception {
        authenticate(REALM_A);
        // Ni /api/p/** ni /p/** → el filtro no extrae slug y pasa.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/widgets");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    /** Ejecuta el filtro contra la URI dada y devuelve la respuesta; la cadena registrada. */
    private MockHttpServletResponse run(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        boolean blocked = response.getStatus() == 401;
        if (blocked) {
            verify(chain, never()).doFilter(request, response);
        } else {
            verify(chain).doFilter(request, response);
        }
        return response;
    }

    private static void authenticate(UUID projectId) {
        var pup = new ProjectUserPrincipal(projectId, USER_ID, "alice", "pw", List.of(), true, 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(pup, null, List.of()));
    }
}
