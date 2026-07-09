package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.identity.application.configuration.NexusOAuthBootstrapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Verifica <b>Pushed Authorization Requests</b> (PAR, RFC 9126): el AS expone
 * {@code /oauth2/par}, lo anuncia en el discovery y, ante una petición autenticada de un
 * cliente confidencial, devuelve un {@code request_uri} que el cliente usa luego en el
 * {@code /authorize} (sin exponer los parámetros por el URL del browser).
 *
 * <p>PAR es opcional por configuración (no fuerza a los clientes existentes), así que este
 * IT asegura únicamente que el endpoint está activo y publicado — tanto en el issuer global
 * como por realm (multi-issuer) — y que un POST válido del cliente bootstrap devuelve la
 * respuesta RFC 9126.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OidcParEndpointIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NexusOAuthBootstrapProperties bootstrapProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void globalDiscoveryAdvertisesParEndpoint() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushed_authorization_request_endpoint").exists())
                .andExpect(jsonPath("$.pushed_authorization_request_endpoint", endsWith("/oauth2/par")));
    }

    @Test
    void perIssuerDiscoveryAdvertisesParEndpointForProjectRealm() throws Exception {
        String slug = "par-" + UUID.randomUUID();
        seedProject(slug);

        mockMvc.perform(get("/p/" + slug + "/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushed_authorization_request_endpoint").exists())
                .andExpect(jsonPath("$.pushed_authorization_request_endpoint",
                        containsString("/p/" + slug + "/oauth2/par")));
    }

    @Test
    void pushedAuthorizationRequestReturnsRequestUri() throws Exception {
        // PKCE es obligatorio en SAS 7.0.5 para auth-code (default seguro OAuth 2.1); la
        // request empujada a /par debe llevar code_challenge. Calculamos un challenge S256
        // desde un verifier fijo (reproducible) y comprobamos la respuesta RFC 9126 §2.2.
        String verifier = "par-it-verifier-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        String challenge = s256Challenge(verifier);

        mockMvc.perform(post("/oauth2/par")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(
                                bootstrapProperties.clientId(), bootstrapProperties.clientSecret()))
                        .param("response_type", "code")
                        .param("client_id", bootstrapProperties.clientId())
                        .param("redirect_uri", bootstrapProperties.redirectUri())
                        .param("scope", "openid profile")
                        .param("state", "par-it")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.request_uri").exists())
                .andExpect(jsonPath("$.expires_in").exists());
    }

    private static String s256Challenge(String verifier) throws java.security.NoSuchAlgorithmException {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private void seedProject(String slug) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), slug, "PAR IT", now, now);
    }
}
