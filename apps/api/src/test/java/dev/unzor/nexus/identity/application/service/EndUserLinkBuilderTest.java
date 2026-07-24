package dev.unzor.nexus.identity.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Los enlaces públicos aterrizan en el origen de la <b>app</b> registrado por el proyecto
 * (self-service: alta, reseteo y login federado se completan en el dominio de la app), con
 * fallback al frontend propio de Nexus cuando el proyecto no declara orígenes.
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
    void passwordResetLinkUsesRegisteredAppOrigin() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip"))
                .thenReturn(Optional.of("https://flip.unzor.dev"));
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        assertThat(builder.passwordResetLink("flip", "raw-token"))
                .isEqualTo("https://flip.unzor.dev/p/flip/password-reset/confirm?token=raw-token");
    }

    @Test
    void passwordResetLinkFallsBackToFrontendBaseUrlWhenNoAppOrigin() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip")).thenReturn(Optional.empty());
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        assertThat(builder.passwordResetLink("flip", "raw-token"))
                .isEqualTo("https://nexus.unzor.dev/p/flip/password-reset/confirm?token=raw-token");
    }

    @Test
    void googleLinkRequiredLinkUsesRegisteredAppOrigin() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip"))
                .thenReturn(Optional.of("https://flip.unzor.dev"));
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        assertThat(builder.googleLinkRequiredLink("flip"))
                .isEqualTo("https://flip.unzor.dev/login?google_link_required=1");
    }

    @Test
    void googleLinkRequiredLinkFallsBackToPortalWhenNoAppOrigin() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip")).thenReturn(Optional.empty());
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        assertThat(builder.googleLinkRequiredLink("flip"))
                .isEqualTo("https://nexus.unzor.dev/p/flip");
    }

    @Test
    void googleErrorLinkUsesRegisteredAppOrigin() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip"))
                .thenReturn(Optional.of("https://flip.unzor.dev"));
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        assertThat(builder.googleErrorLink("flip", "provider_error"))
                .isEqualTo("https://flip.unzor.dev/login?google_error=provider_error");
    }

    @Test
    void appLandingLinkUsesRegisteredAppOrigin() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip"))
                .thenReturn(Optional.of("https://flip.unzor.dev"));
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        assertThat(builder.appLandingLink("flip"))
                .isEqualTo("https://flip.unzor.dev/login?from=google");
    }

    @Test
    void appLandingLinkFallsBackToPortalWhenNoAppOrigin() {
        when(allowedOriginsResolver.primaryProjectOrigin("flip")).thenReturn(Optional.empty());
        EndUserLinkBuilder builder = new EndUserLinkBuilder(FRONTEND_BASE_URL, allowedOriginsResolver);

        assertThat(builder.appLandingLink("flip"))
                .isEqualTo("https://nexus.unzor.dev/p/flip");
    }
}
