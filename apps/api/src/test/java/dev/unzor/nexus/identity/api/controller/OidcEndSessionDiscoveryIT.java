package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * proyecto con la URL correcta, y (2) que el endpoint está realmente mapeado a la cadena
 * del AS (el {@code endpointsMatcher} multi-issuer cubre {@code /connect/**}, no sólo
 * {@code /oauth2/**}) — de otro modo el RP redirigiría a una URL anunciada que devuelve
 * 403/404. La validación de {@code post_logout_redirect_uri} contra los URIs registrados
 * del cliente la provee SAS por defecto.</p>
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
    void endSessionEndpointIsMappedToAuthorizationServerChain() throws Exception {
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

    private void seedProject(UUID projectId, String slug) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                projectId, slug, "End-session IT", now, now);
    }
}
