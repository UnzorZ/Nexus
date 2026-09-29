package dev.unzor.nexus.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.identity.domain.exception.WeakPasswordException;

/**
 * Política de contraseñas del alta pública (auditoría 2026-09-28, N1).
 *
 * <p>Modos de fallo escritos antes del código:
 * <ul>
 *   <li>El alta acepta "123456" (el reset exige 8 caracteres: los dos caminos no coinciden).</li>
 *   <li>El alta acepta una contraseña de 8+ caracteres de las más comunes, o igual al email.</li>
 *   <li>Una contraseña correcta deja de poder registrarse (regresión).</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegistrationPasswordPolicyIT {

    @Autowired private RegisterProjectUserService registerService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID projectId;

    @BeforeEach
    void publicProject() {
        projectId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO projects (id, slug, name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                projectId, "pwd-" + projectId, "Password IT", now, now);
        jdbcTemplate.update("UPDATE projects SET public_registration_enabled = TRUE WHERE id = ?", projectId);
    }

    @Test
    void weakPasswordsAreRefusedAtRegistration() {
        for (String weak : new String[] {"123456", "short1", "password", "12345678", "qwertyuiop", "Password123"}) {
            assertThatThrownBy(() -> registerService.register(projectId, unique(), weak, "U", null))
                    .as(weak).isInstanceOf(WeakPasswordException.class);
        }
        String email = unique();
        assertThatThrownBy(() -> registerService.register(projectId, email, email, "U", null))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void aReasonablePasswordStillRegisters() {
        assertThat(registerService.register(projectId, unique(), "Correct-Horse-7", "U", null)).isNotNull();
    }

    private static String unique() {
        return "u-" + UUID.randomUUID() + "@policy.test";
    }
}
