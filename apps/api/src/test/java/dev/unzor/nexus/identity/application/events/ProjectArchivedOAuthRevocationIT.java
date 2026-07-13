package dev.unzor.nexus.identity.application.events;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.identity.application.service.ProjectUserOAuthRevocationService;
import dev.unzor.nexus.identity.application.service.ProjectUserSessionService;
import dev.unzor.nexus.projects.application.service.ArchiveProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Demuestra que las revocaciones OAuth y de sesión son síncronas y participan en
 * la misma transacción que la mutación del proyecto.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProjectArchivedOAuthRevocationIT {

    private static final UUID PROJECT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000005a1");

    @Autowired
    private ArchiveProjectService archiveProjectService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ProjectUserOAuthRevocationService revocationService;

    @MockitoBean
    private ProjectUserSessionService sessionService;

    @BeforeEach
    void seedProject() {
        reset(revocationService, sessionService);
        jdbc.update("DELETE FROM projects WHERE id = ?", PROJECT_ID);
        jdbc.update("INSERT INTO projects (id, slug, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Archive listener IT', 'ACTIVE', now(), now())",
                PROJECT_ID, "archive-listener-" + UUID.randomUUID());
    }

    @Test
    void invokesRevocationSynchronouslyForARealArchive() {
        archiveProjectService.archive(PROJECT_ID, UUID.randomUUID());

        verify(revocationService).revokeForProject(PROJECT_ID);
        verify(sessionService).revokeAllForProject(PROJECT_ID);
        assertThat(projectStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    void rollsBackArchiveWhenOAuthRevocationFails() {
        doThrow(new DataAccessResourceFailureException("PostgreSQL statement failed"))
                .when(revocationService).revokeForProject(PROJECT_ID);

        assertThatThrownBy(() -> archiveProjectService.archive(PROJECT_ID, UUID.randomUUID()))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(projectStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rollsBackArchiveWhenRedisSessionRevocationFails() {
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(sessionService).revokeAllForProject(PROJECT_ID);

        assertThatThrownBy(() -> archiveProjectService.archive(PROJECT_ID, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis unavailable");

        verify(revocationService).revokeForProject(PROJECT_ID);
        assertThat(projectStatus()).isEqualTo("ACTIVE");
    }

    private String projectStatus() {
        return jdbc.queryForObject("SELECT status FROM projects WHERE id = ?", String.class, PROJECT_ID);
    }
}
