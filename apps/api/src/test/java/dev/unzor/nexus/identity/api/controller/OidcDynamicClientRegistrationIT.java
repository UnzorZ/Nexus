package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica <b>Dynamic Client Registration</b> (RFC 7591): el AS expone
 * {@code /oauth2/register}, lo anuncia en los metadatos OAuth2, y ante un POST de registro
 * (per-issuer {@code /p/{slug}/oauth2/register}) abierto devuelve un nuevo {@code client_id}
 * + {@code client_secret} (§3.2). Como el repo de clientes es project-scoped, el cliente
 * queda persistido en {@code project_oauth_clients} del proyecto {slug} — no en la tabla
 * global — con su secreto hasheado (bcrypt).
 *
 * <p>Usamos el endpoint RFC 7591 ({@code /oauth2/register}, bajo el árbol oauth2 que el
 * matcher del AS reclama nativo) con {@code openRegistrationAllowed(true)}; el variant
 * OIDC {@code /connect/register} NO expone registro abierto en SAS y exige un Initial
 * Access Token, así que queda fuera.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OidcDynamicClientRegistrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registerCreatesProjectScopedClient() throws Exception {
        SeededProject p = seedProject();

        mockMvc.perform(post("/p/" + p.slug + "/oauth2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "redirect_uris": ["https://example.com/callback"],
                                  "grant_types": ["authorization_code", "refresh_token"],
                                  "token_endpoint_auth_method": "client_secret_basic",
                                  "client_name": "DCR Test Client"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client_id").exists())
                .andExpect(jsonPath("$.client_secret").exists());

        // El cliente quedó en project_oauth_clients (NO en la tabla global) y asociado al
        // proyecto correcto — prueba de que el DCR es project-scoped. El proyecto es único
        // por test, así que es el único cliente bajo él.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT client_secret_hash FROM project_oauth_clients WHERE project_id = ?",
                p.projectId);
        org.assertj.core.api.Assertions.assertThat(rows).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(rows.get(0).get("client_secret_hash"))
                .asString().startsWith("$2a$");
    }

    @Test
    void dcrClientWithoutScopeGetsDefaultOidcScopes() throws Exception {
        // SAS 7.0.5 rechaza `scope` en DCR por diseño y su converter no aplica scopes por
        // defecto, así que un cliente DCR llegaría con cero scopes → no podría hacer OIDC.
        // Nexus asigna los scopes OIDC estándar (openid, profile) por defecto (los permisos
        // viajan en el claim `permissions`, no como scopes). Este test fija ese comportamiento.
        SeededProject p = seedProject();

        mockMvc.perform(post("/p/" + p.slug + "/oauth2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "redirect_uris": ["https://example.com/callback"],
                                  "grant_types": ["authorization_code", "refresh_token"],
                                  "token_endpoint_auth_method": "client_secret_basic",
                                  "client_name": "DCR Default Scope Client"
                                }
                                """))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT scopes FROM project_oauth_clients WHERE project_id = ?", p.projectId);
        org.assertj.core.api.Assertions.assertThat(rows).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(rows.get(0).get("scopes"))
                .asString()
                .contains("openid", "profile");
    }

    @Test
    void dcrRejectsDeclaredScopeByDesign() throws Exception {
        // Guard: SAS rechaza `scope` en el registro dinámico (invalid_scope) — postura de
        // seguridad (el AS controla los scopes; un cliente auto-registrado no se auto-concede
        // nada). Nexus lo respeta: los clientes DCR reciben DEFAULT_DCR_SCOPES en su lugar.
        // Si este test empieza a fallar (pasa a 201), alguien permitió scope arbitrario en
        // DCR — revisar el validador del AS antes de aceptarlo.
        SeededProject p = seedProject();

        mockMvc.perform(post("/p/" + p.slug + "/oauth2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "redirect_uris": ["https://example.com/callback"],
                                  "grant_types": ["authorization_code", "refresh_token"],
                                  "token_endpoint_auth_method": "client_secret_basic",
                                  "client_name": "DCR Scoped Client",
                                  "scope": "openid profile"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scope"));
    }

    @Test
    void dcrClientAuthenticatesWithItsSecret() throws Exception {
        // Regresión del doble-hash: el provider de DCR de SAS pre-codifica el secreto con el
        // DelegatingPasswordEncoder ("{bcrypt}$2a$…"); si persistProjectClient lo recodificaba,
        // client-auth fallaba con 401 invalid_client. Tras el fix, el cliente DCR autentica con
        // el secreto devuelto en el registro. Lo probamos vía PAR (exige PKCE + client_secret_basic).
        SeededProject p = seedProject();

        String response = mockMvc.perform(post("/p/" + p.slug + "/oauth2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "redirect_uris": ["https://example.com/callback"],
                                  "grant_types": ["authorization_code", "refresh_token"],
                                  "token_endpoint_auth_method": "client_secret_basic",
                                  "client_name": "DCR Auth Client"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> dcrResponse =
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(response, java.util.Map.class);
        String clientId = (String) dcrResponse.get("client_id");
        String clientSecret = (String) dcrResponse.get("client_secret");

        // PAR requiere PKCE; code_challenge S256 fijo para el test.
        String challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        mockMvc.perform(post("/p/" + p.slug + "/oauth2/par")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(clientId, clientSecret))
                        .param("client_id", clientId)
                        .param("redirect_uri", "https://example.com/callback")
                        .param("response_type", "code")
                        .param("scope", "openid profile")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.request_uri").exists());
    }

    private SeededProject seedProject() {
        String slug = "dcr-" + UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                projectId, slug, "DCR IT", now, now);
        return new SeededProject(slug, projectId);
    }

    private record SeededProject(String slug, UUID projectId) {
    }
}
