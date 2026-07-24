package dev.unzor.nexus.identity.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * El enlace de verificación de email aterriza en el origen de la <b>app</b> registrado por
 * el proyecto (self-service: el alta se completa en el dominio de la app), con fallback al
 * frontend propio de Nexus. El reseteo de contraseña sigue en el frontend de Nexus (la app
 * aún no hostea esa página) —se documenta la asimetría para evitar regresiones—.
 */
@ExtendWith(MockitoExtension.class)
class EndUserLinkBuilderTest {

    private static final String FRONTEND_BASE_URL = "https://nexus.unzor.dev";

    @Mock
    private ProjectAllowedOriginsResolver allowedOriginsResolver;

    @Test
    void verifyEmailLinkUsesRegisteredAppOrigin() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip"))
                .thenReturn(Optional.of("https://flip.unzor.dev"));
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        String link = builder.verifyEmailLink("flip", "raw-token");

        assertThat(link).isEqualTo("https://flip.unzor.dev/p/flip/verify-email?token=raw-token");
    }

    @Test
    void verifyEmailLinkFallsBackToFrontendBaseUrlWhenNoAppOrigin() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip")).thenReturn(Optional.empty());
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        String link = builder.verifyEmailLink("flip", "raw-token");

        assertThat(link).isEqualTo("https://nexus.unzor.dev/p/flip/verify-email?token=raw-token");
    }

    @Test
    void verifyEmailLinkStripsTrailingSlashFromAppOrigin() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip"))
                .thenReturn(Optional.of("https://flip.unzor.dev/"));
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        assertThat(builder.verifyEmailLink("flip", "t"))
                .isEqualTo("https://flip.unzor.dev/p/flip/verify-email?token=t");
    }

    @Test
    void verifyEmailLinkUrlEncodesTheToken() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip"))
                .thenReturn(Optional.of("https://flip.unzor.dev"));
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        // Tokens opacos pueden contener caracteres con significado en query strings.
        assertThat(builder.verifyEmailLink("flip", "a+b/c"))
                .isEqualTo("https://flip.unzor.dev/p/flip/verify-email?token=a%2Bb%2Fc");
    }

    @Test
    void passwordResetLinkStaysOnFrontendBaseUrl() {
        // Asimetría documentada: el reseteo de contraseña no se rutea al origen de la app
        // (la app aún no hostea /password-reset/confirm). Lock para evitar regresiones.
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        assertThat(builder.passwordResetLink("flip", "raw-token"))
                .isEqualTo("https://nexus.unzor.dev/p/flip/password-reset/confirm?token=raw-token");
    }
}
