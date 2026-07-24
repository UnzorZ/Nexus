package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS dinámico de {@code /api/p/**}: el origen de la redirect URI registrada de un
 * cliente ACTIVO se permite (con credenciales); un origen no registrado se rechaza con
 * 403; un cliente DISABLED no abre su origen. Establece el patrón de aserción CORS
 * (no existía ninguno en el repo). El origen permitido proviene <b>únicamente</b> de la
 * redirect URI registrada —sin tocar la configuración de la instancia—.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectEndUserCorsIntegrationTests {

    private static final String REGISTERED_ORIGIN = "https://flip.unzor.dev";
    private static final String REGISTERED_REDIRECT = REGISTERED_ORIGIN + "/login/oauth2/code/nexus";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> projectIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID projectId : projectIds) {
            jdbc.update("DELETE FROM project_oauth_clients WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM projects WHERE id = ?", projectId);
        }
    }

    @Test
    void registeredOriginIsAllowedWithCredentials() throws Exception {
        Seeded seeded = seedClient("ACTIVE", REGISTERED_REDIRECT);

        mockMvc.perform(get("/api/p/" + seeded.slug + "/csrf").header("Origin", REGISTERED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", REGISTERED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void unregisteredOriginIsRejectedWith403() throws Exception {
        Seeded seeded = seedClient("ACTIVE", REGISTERED_REDIRECT);

        mockMvc.perform(get("/api/p/" + seeded.slug + "/csrf").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightFromRegisteredOriginIsAllowed() throws Exception {
        Seeded seeded = seedClient("ACTIVE", REGISTERED_REDIRECT);

        mockMvc.perform(options("/api/p/" + seeded.slug + "/login")
                        .header("Origin", REGISTERED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", REGISTERED_ORIGIN));
    }

    @Test
    void disabledClientDoesNotOpenItsOrigin() throws Exception {
        Seeded seeded = seedClient("DISABLED", REGISTERED_REDIRECT);

        mockMvc.perform(get("/api/p/" + seeded.slug + "/csrf").header("Origin", REGISTERED_ORIGIN))
                .andExpect(status().isForbidden());
    }

    private Seeded seedClient(String status, String redirectUri) {
        UUID projectId = UUID.randomUUID();
        projectIds.add(projectId);
        String slug = "cors-it-" + projectId.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'CORS IT', ?, ?, ?)",
                projectId, slug, ProjectStatus.ACTIVE.name(), now, now);
        UUID clientDatabaseId = UUID.randomUUID();
        jdbc.update("INSERT INTO project_oauth_clients "
                        + "(id, project_id, client_id, client_secret_hash, name, redirect_uris, "
                        + "post_logout_redirect_uris, grant_types, scopes, require_pkce, consent_required, "
                        + "status, created_by_account_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, 'CORS client', ?, '', 'authorization_code', 'openid\nprofile', "
                        + "true, false, ?, NULL, ?, ?)",
                clientDatabaseId, projectId, "nxo-cors-" + clientDatabaseId.toString().substring(0, 8),
                redirectUri, status, now, now);
        return new Seeded(projectId, slug);
    }

    private record Seeded(UUID projectId, String slug) {
    }
}
