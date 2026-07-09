package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
