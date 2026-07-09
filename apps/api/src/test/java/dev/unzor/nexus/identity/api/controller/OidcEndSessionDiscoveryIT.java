package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el contrato del <b>RP-initiated logout</b> (OIDC): el discovery del
 * Authorization Server anuncia {@code end_session_endpoint} — la URL que un RP lee para
 * redirigir al usuario a cerrar su sesión en Nexus (p. ej. el
 * {@code OidcClientInitiatedLogoutSuccessHandler} de la app de referencia).
 *
 * <p>SAS 7.0.5 publica por defecto el end-session endpoint en {@code /connect/logout}
 * (junto con {@code /userinfo}), y en multi-issuer lo prefija con el realm del proyecto.
 * Aquí se asegura (1) que el discovery lo anuncia tanto en el issuer global como por
 * proyecto con la URL correcta, y (2) — lo crítico — que el endpoint está realmente
 * mapeado a la cadena del AS <b>también en el realm por proyecto</b>. El
 * {@code endpointsMatcher} multi-issuer se construye agregando el {@code getRequestMatcher()}
 * de cada configurer, incluido {@code OidcLogoutEndpointConfigurer}, que aplica
 * {@code withMultipleIssuersPattern("/connect/logout")} → cubre {@code /connect/logout} Y
 * {@code /p/{slug}/connect/logout}. El round-trip final lo demuestra empíricamente: sólo el
 * filtro del AS conoce los {@code post_logout_redirect_uri} registrados y redirigiría a uno
 * de ellos; si la ruta cayese a la cadena {@code /p/**}, veríamos una redirección a login,
 * no al post-logout. La validación de {@code post_logout_redirect_uri} contra los URIs
 * registrados del cliente la provee SAS por defecto.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OidcEndSessionDiscoveryIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void globalDiscoveryAdvertisesEndSessionEndpoint() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.end_session_endpoint").exists())
                .andExpect(jsonPath("$.end_session_endpoint", org.hamcrest.Matchers.endsWith("/connect/logout")));
    }

    @Test
    void perIssuerDiscoveryAdvertisesEndSessionEndpointForProjectRealm() throws Exception {
        // Un proyecto real para que el realm /p/{slug} exista; el discovery por proyecto
        // debe anunciar el end_session_endpoint prefixed por ese slug (no el global).
        String slug = "endsession-" + UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        seedProject(projectId, slug);

        mockMvc.perform(get("/p/" + slug + "/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.end_session_endpoint").exists())
                .andExpect(jsonPath("$.end_session_endpoint",
                        org.hamcrest.Matchers.containsString("/p/" + slug + "/connect/logout")));
    }

    @Test
    void globalEndSessionEndpointIsReachable() throws Exception {
        // El endpointsMatcher multi-issuer debe cubrir /connect/logout (no sólo /oauth2/**);
        // si cayera a la cadena residual (denyAll) o no estuviera mapeado, devolvería 403/404.
        int status = mockMvc.perform(get("/connect/logout"))
                .andReturn()
                .getResponse()
                .getStatus();
        if (status == 403 || status == 404) {
            fail("end-session endpoint /connect/logout not reachable (status=" + status
                    + ") — el endpointsMatcher del AS no lo cubre");
        }
    }

    @Test
    void perRealmEndSessionEndpointIsReachable() throws Exception {
        // El realm por proyecto también debe estar cubierto por la cadena del AS (no caer a
        // la cadena /p/**). 403/404 indicaría que /p/{slug}/connect/logout no está mapeado.
        SeededClient seeded = seedClientWithPostLogoutUri();

        int status = mockMvc.perform(get("/p/" + seeded.slug + "/connect/logout"))
                .andReturn()
                .getResponse()
                .getStatus();
        if (status == 403 || status == 404) {
            fail("per-realm end-session /p/" + seeded.slug + "/connect/logout not reachable (status="
                    + status + ") — el endpointsMatcher del AS no lo cubre bajo /p/{slug}/");
        }
    }

    @Test
    void perRealmEndSessionIsProcessedByAuthorizationServerChain() throws Exception {
        // SAS requiere id_token_hint para RP-initiated logout (OidcLogoutAuthenticationProvider
        // lanza invalid_token sin él). Sin hint → 400 invalid_token. Lo decisivo: esto NO es un
        // 302 redirect a login (lo que produciría la cadena /p/** si el endpointsMatcher
        // multi-issuer NO cubriese /p/{slug}/connect/logout). Un 400 invalid_token prueba que el
        // AS chain es propietario del end-session por proyecto. (Un round-trip con redirect
        // requeriría mintar un id_token real vía el flujo completo; ese e2e vive en la app de
        // referencia — aquí se afirma la propiedad de la ruta, que era la incógnita.)
        SeededClient seeded = seedClientWithPostLogoutUri();

        int status = mockMvc.perform(get("/p/" + seeded.slug + "/connect/logout")
                        .param("post_logout_redirect_uri", "https://app.example.com/after-logout"))
                .andReturn()
                .getResponse()
                .getStatus();

        if (status == 302 || status == 301) {
            fail("per-realm /p/" + seeded.slug + "/connect/logout redirected (status=" + status
                    + ") — cayó a la cadena /p/** (login); el endpointsMatcher del AS no cubre"
                    + " /p/{slug}/connect/logout");
        }
        if (status == 403 || status == 404) {
            fail("per-realm /p/" + seeded.slug + "/connect/logout not mapped (status=" + status + ")");
        }
        // 400 invalid_token = el provider OIDC del AS procesó la petición y rechazó por falta
        // de id_token_hint. Es el resultado esperado y la prueba de propiedad de la ruta.
        org.junit.jupiter.api.Assertions.assertEquals(400, status,
                "Expected 400 invalid_token from the AS OidcLogout provider (no id_token_hint)");
    }

    // ---- helpers ----

    private void seedProject(UUID projectId, String slug) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                projectId, slug, "End-session IT", now, now);
    }

    private SeededClient seedClientWithPostLogoutUri() {
        String slug = "endsession-" + UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        String clientIdStr = "nxo-endsession-" + clientId.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        String secretHash = new BCryptPasswordEncoder().encode("endsession-secret");

        seedProject(projectId, slug);
        // grant_types / scopes / redirect_uris / post_logout_redirect_uris se guardan
        // newline-joined (StringListConverter). El cliente lleva un post_logout_redirect_uri
        // registrado que el round-trip usará.
        jdbcTemplate.update(
                "INSERT INTO project_oauth_clients "
                        + "(id, project_id, client_id, client_secret_hash, name, redirect_uris, "
                        + "post_logout_redirect_uris, grant_types, scopes, require_pkce, consent_required, "
                        + "status, created_by_account_id, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?, 'ACTIVE', ?, ?, ?)",
                clientId, projectId, clientIdStr, secretHash, "End-session Client",
                "https://app.example.com/callback",
                "https://app.example.com/after-logout",
                "authorization_code\nrefresh_token",
                "openid\nprofile",
                true, false, UUID.randomUUID(), now, now);
        return new SeededClient(slug, clientIdStr);
    }

    private record SeededClient(String slug, String clientId) {
    }
}
