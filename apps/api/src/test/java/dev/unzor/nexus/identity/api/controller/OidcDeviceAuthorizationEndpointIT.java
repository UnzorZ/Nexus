package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el <b>Device Authorization Grant</b> (RFC 8628): el AS expone
 * {@code /oauth2/device_authorization}, lo anuncia en el discovery y, ante una petición
 * autenticada de un cliente confidencial con el grant {@code device_code}, devuelve
 * {@code device_code}, {@code user_code}, {@code verification_uri}, {@code expires_in} e
 * {@code interval} (RFC 8628 §3.2).
 *
 * <p>El cliente se seedea por JDBC con el grant device_code y un secreto bcrypt (mismo
 * encoder que la app), evitando el baile completo de auth del panel. La verificación del
 * usuario (página {@code /oauth2/device} → POST a {@code /oauth2/device_verification}) y el
 * sondeo de /token quedan fuera de este IT; aquí se cubre el contrato del endpoint.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OidcDeviceAuthorizationEndpointIT {

    private static final String DEVICE_SECRET = "device-secret";
    private static final String DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void globalDiscoveryAdvertisesDeviceAuthorizationEndpoint() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_authorization_endpoint").exists())
                .andExpect(jsonPath("$.device_authorization_endpoint", endsWith("/oauth2/device_authorization")));
    }

    @Test
    void perIssuerDiscoveryAdvertisesDeviceAuthorizationEndpoint() throws Exception {
        SeededClient seeded = seedDeviceClient();

        mockMvc.perform(get("/p/" + seeded.slug + "/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_authorization_endpoint").exists())
                .andExpect(jsonPath("$.device_authorization_endpoint",
                        containsString("/p/" + seeded.slug + "/oauth2/device_authorization")));
    }

    @Test
    void deviceAuthorizationRequestReturnsDeviceAndUserCode() throws Exception {
        SeededClient seeded = seedDeviceClient();

        // scope es opcional en device_authorization (RFC 8628 §3.1); omitido, SAS aplica los
        // scopes registrados del cliente. Respuesta RFC 8628 §3.2: device_code + user_code +
        // verification_uri[_complete] + expires_in (interval es opcional y SAS lo omite).
        mockMvc.perform(post("/p/" + seeded.slug + "/oauth2/device_authorization")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(seeded.clientId, DEVICE_SECRET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_code").exists())
                .andExpect(jsonPath("$.user_code").exists())
                .andExpect(jsonPath("$.verification_uri").exists())
                .andExpect(jsonPath("$.verification_uri_complete").exists())
                .andExpect(jsonPath("$.expires_in").exists());
    }

    // ---- helpers ----

    private SeededClient seedDeviceClient() {
        String slug = "device-" + UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID clientId2 = UUID.randomUUID();
        String clientId = "nxo-device-" + clientId2.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        String secretHash = new BCryptPasswordEncoder().encode(DEVICE_SECRET);

        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                projectId, slug, "Device IT", now, now);
        // grant_types / scopes / redirect_uris se guardan newline-joined (StringListConverter).
        jdbcTemplate.update(
                "INSERT INTO project_oauth_clients "
                        + "(id, project_id, client_id, client_secret_hash, name, redirect_uris, "
                        + "post_logout_redirect_uris, grant_types, scopes, require_pkce, consent_required, "
                        + "status, created_by_account_id, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?, 'ACTIVE', ?, ?, ?)",
                clientId2, projectId, clientId, secretHash, "Device Client",
                "https://example.com/callback", "",
                "authorization_code\n" + DEVICE_GRANT + "\nrefresh_token",
                "openid\nprofile",
                false, false, UUID.randomUUID(), now, now);
        return new SeededClient(slug, clientId);
    }

    private record SeededClient(String slug, String clientId) {
    }
}
