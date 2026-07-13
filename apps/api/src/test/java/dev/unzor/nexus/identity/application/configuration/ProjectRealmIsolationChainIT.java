package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT de aislamiento entre realms a través de la selección <b>real</b> de
 * {@link org.springframework.security.web.SecurityFilterChain}: un
 * {@link ProjectUserPrincipal} del realm A debe recibir 401 al alcanzar endpoints del
 * realm B, tanto en la cadena de la API JSON ({@code /api/p/{slug}/me}) como —sobre
 * todo— en la del Authorization Server @Order(1) ({@code /p/{slug}/oauth2/authorize}),
 * que es la que el filtro de la primera iteración no cubría.
 *
 * <p>Autentica vía {@code SecurityMockMvcRequestPostProcessors.authentication}, que
 * atraviesa la cadena real de filtros (no un mock), con un ProjectUserPrincipal del
 * projectId dado. El proyecto del realm B se siembra en BD para que la resolución
 * slug→projectId del filtro tenga contra qué comparar.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectRealmIsolationChainIT {

    private static final String SLUG_B = "realm-iso-" + UUID.randomUUID();
    private static final String ARCHIVED_SLUG = "realm-archived-" + UUID.randomUUID();
    private static final UUID REALM_B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID REALM_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ARCHIVED_REALM = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedRealmB() {
        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Realm IT', 'ACTIVE', now(), now()) ON CONFLICT (slug) DO NOTHING",
                REALM_B, SLUG_B);
        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Archived Realm IT', 'ARCHIVED', now(), now()) "
                        + "ON CONFLICT (slug) DO NOTHING",
                ARCHIVED_REALM, ARCHIVED_SLUG);
    }

    @Test
    void crossRealmPrincipalIsBlockedOnEndUserApi() throws Exception {
        mockMvc.perform(get("/api/p/" + SLUG_B + "/me").with(authentication(authFor(REALM_A))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossRealmPrincipalIsBlockedOnAuthorizationServerAuthorize() throws Exception {
        // La cadena AS @Order(1) captura /p/{slug}/oauth2/**; el filtro debe bloquear
        // aquí al principal cross-realm ANTES de que SAS procese el authorize.
        mockMvc.perform(get("/p/" + SLUG_B + "/oauth2/authorize").with(authentication(authFor(REALM_A))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void matchingRealmPrincipalPassesTheFilter() throws Exception {
        // Control: el principal del propio realm NO es bloqueado por el filtro. /me
        // llega al controller y devuelve 404 (no hay ProjectUser en BD para ese
        // userId aleatorio) — lo importante es que NO sea 401.
        mockMvc.perform(get("/api/p/" + SLUG_B + "/me").with(authentication(authFor(REALM_B))))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossRealmPrincipalIsStillBlockedWhenTargetRealmIsArchived() throws Exception {
        // M1 usa resolución de existencia: el estado inactivo no puede convertir una
        // petición cross-realm en una respuesta distinta del 401 de aislamiento.
        mockMvc.perform(get("/api/p/" + ARCHIVED_SLUG + "/me").with(authentication(authFor(REALM_A))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void matchingPrincipalOnArchivedRealmReachesOperationalGate() throws Exception {
        mockMvc.perform(get("/api/p/" + ARCHIVED_SLUG + "/me")
                        .with(authentication(authFor(ARCHIVED_REALM))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("project_not_operational"));
    }

    @Test
    void archivedRealmPrincipalCanStillManageOwnSessionsForTeardown() throws Exception {
        // F1: logout y autorrevocación de sesiones son operaciones de teardown y no deben
        // caer en el gate operacional — un usuario debe poder salir de un realm
        // decomisionado. /me (runtime read) sigue gated (test anterior); /sessions no.
        mockMvc.perform(get("/api/p/" + ARCHIVED_SLUG + "/sessions")
                        .with(authentication(authFor(ARCHIVED_REALM))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/p/" + ARCHIVED_SLUG + "/sessions")
                        .with(authentication(authFor(ARCHIVED_REALM))).with(csrf()))
                .andExpect(status().isNoContent());
    }

    /** Authentication con un ProjectUserPrincipal del projectId dado (como tras el login). */
    private static Authentication authFor(UUID projectId) {
        var principal = new ProjectUserPrincipal(
                projectId, UUID.randomUUID(), "alice", null, List.of(), true, 1L);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
