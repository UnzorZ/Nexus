package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.application.configuration.IdentityLoginProperties;
import dev.unzor.nexus.identity.application.service.RecordProjectUserLoginService;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.exception.EmailNotVerifiedException;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSessionAuthenticatorTests {

    private static final List<SimpleGrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_PROJECT_USER"));

    private final ProjectUserUserDetailsService userDetailsService = mock(ProjectUserUserDetailsService.class);
    private final ProjectUserRepository repository = mock(ProjectUserRepository.class);
    private final RecordProjectUserLoginService recordLoginService = mock(RecordProjectUserLoginService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final IdentityLoginProperties loginProperties = new IdentityLoginProperties(5, Duration.ofMinutes(15));

    private final ProjectSessionAuthenticator authenticator = new ProjectSessionAuthenticator(
            userDetailsService, repository, encoder, recordLoginService, eventPublisher, loginProperties);

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void successfulLoginRotatesSessionRecordsLoginAndAuditsAsProjectUser() {
        UUID projectId = UUID.randomUUID();
        ProjectUser user = activeUser(projectId, "neo@example.com", "secret123");
        when(userDetailsService.loadProjectUser(projectId, "neo@example.com"))
                .thenReturn(ProjectUserPrincipal.from(user, AUTHORITIES));
        when(repository.findByProjectIdAndId(projectId, user.getId())).thenReturn(Optional.of(user));

        Authentication result = authenticator.authenticate(
                projectId, "neo@example.com", "secret123", request, response);

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(((ProjectUserPrincipal) result.getPrincipal()).password()).isNull(); // sin credenciales
        // Spring Security 7.0 deriva auth_time del id_token del FactorGrantedAuthority; el
        // authenticator manual debe añadir FACTOR_PASSWORD (igual que DaoAuthenticationProvider).
        assertThat(result.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("ROLE_PROJECT_USER", FactorGrantedAuthority.PASSWORD_AUTHORITY);
        // Anti session-fixation: se creó y rotó la sesión.
        assertThat(request.getSession(false)).isNotNull();
        verify(recordLoginService).recordLogin(projectId, user.getId());
        assertAudited("project_user.login_succeeded", user.getId());
    }

    @Test
    void unknownUserFailsWithGenericErrorAndNoRecordLogin() {
        UUID projectId = UUID.randomUUID();
        when(userDetailsService.loadProjectUser(projectId, "ghost@example.com"))
                .thenThrow(new UsernameNotFoundException("not found"));

        assertThatThrownBy(() -> authenticator.authenticate(
                projectId, "ghost@example.com", "whatever", request, response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(ProjectSessionAuthenticator.GENERIC_ERROR);

        verify(recordLoginService, never()).recordLogin(any(), any());
        assertAudited("project_user.login_failed", null);
    }

    @Test
    void unknownUserStillRunsBcryptToEqualizeTiming() {
        // La rama "usuario inexistente" ejecuta un matches() contra un hash dummy para
        // igualar tiempos (anti-enumeración). Lo verificamos vía un spy del encoder.
        BCryptPasswordEncoder spyEncoder = spy(new BCryptPasswordEncoder());
        ProjectSessionAuthenticator authenticatorWithSpy = new ProjectSessionAuthenticator(
                userDetailsService, repository, spyEncoder, recordLoginService, eventPublisher, loginProperties);
        UUID projectId = UUID.randomUUID();
        when(userDetailsService.loadProjectUser(projectId, "ghost@example.com"))
                .thenThrow(new UsernameNotFoundException("not found"));

        assertThatThrownBy(() -> authenticatorWithSpy.authenticate(
                projectId, "ghost@example.com", "whatever", request, response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(ProjectSessionAuthenticator.GENERIC_ERROR);

        verify(spyEncoder).matches(eq("whatever"), anyString());
    }

    @Test
    void suspendedUserFailsWithSameGenericError() {
        UUID projectId = UUID.randomUUID();
        ProjectUser user = suspendedUser(projectId, "neo@example.com", "secret123");
        when(userDetailsService.loadProjectUser(projectId, "neo@example.com"))
                .thenReturn(ProjectUserPrincipal.from(user, AUTHORITIES));
        when(repository.findByProjectIdAndId(projectId, user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticator.authenticate(
                projectId, "neo@example.com", "secret123", request, response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(ProjectSessionAuthenticator.GENERIC_ERROR);

        verify(recordLoginService, never()).recordLogin(any(), any());
        assertAudited("project_user.login_failed", user.getId());
    }

    @Test
    void unverifiedUserWithCorrectPasswordThrowsEmailNotVerified() {
        // PENDING_VERIFICATION + contraseña correcta → EmailNotVerifiedException (tras
        // confirmar la contraseña, sin riesgo de enumeración). No registra login.
        UUID projectId = UUID.randomUUID();
        ProjectUser user = pendingUser(projectId, "neo@example.com", "secret123");
        when(userDetailsService.loadProjectUser(projectId, "neo@example.com"))
                .thenReturn(ProjectUserPrincipal.from(user, AUTHORITIES));
        when(repository.findByProjectIdAndId(projectId, user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticator.authenticate(
                projectId, "neo@example.com", "secret123", request, response))
                .isInstanceOf(EmailNotVerifiedException.class);

        verify(recordLoginService, never()).recordLogin(any(), any());
    }

    @Test
    void wrongPasswordFailsWithSameGenericError() {
        UUID projectId = UUID.randomUUID();
        ProjectUser user = activeUser(projectId, "neo@example.com", "secret123");
        when(userDetailsService.loadProjectUser(projectId, "neo@example.com"))
                .thenReturn(ProjectUserPrincipal.from(user, AUTHORITIES));
        when(repository.findByProjectIdAndId(projectId, user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticator.authenticate(
                projectId, "neo@example.com", "WRONG-password", request, response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(ProjectSessionAuthenticator.GENERIC_ERROR);

        verify(recordLoginService, never()).recordLogin(any(), any());
        assertAudited("project_user.login_failed", user.getId());
    }

    @Test
    void lockedUserIsRejectedEvenWithCorrectPassword() {
        UUID projectId = UUID.randomUUID();
        ProjectUser user = activeUser(projectId, "neo@example.com", "secret123");
        lockNow(user); // 5 intentos fallidos -> bloqueado 15m
        when(userDetailsService.loadProjectUser(projectId, "neo@example.com"))
                .thenReturn(ProjectUserPrincipal.from(user, AUTHORITIES));
        when(repository.findByProjectIdAndId(projectId, user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticator.authenticate(
                projectId, "neo@example.com", "secret123", request, response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(ProjectSessionAuthenticator.GENERIC_ERROR);

        verify(recordLoginService, never()).recordLogin(any(), any());
        assertAudited("project_user.login_failed", user.getId());
    }

    @Test
    void repeatedWrongPasswordsLockTheAccount() {
        UUID projectId = UUID.randomUUID();
        ProjectUser user = activeUser(projectId, "neo@example.com", "secret123");
        when(userDetailsService.loadProjectUser(projectId, "neo@example.com"))
                .thenReturn(ProjectUserPrincipal.from(user, AUTHORITIES));
        when(repository.findByProjectIdAndId(projectId, user.getId())).thenReturn(Optional.of(user));

        // 5 intentos fallidos (maxAttempts=5) -> la cuenta queda bloqueada.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authenticator.authenticate(
                    projectId, "neo@example.com", "WRONG", request, response))
                    .isInstanceOf(BadCredentialsException.class);
        }
        assertThat(user.isLocked(Instant.now())).isTrue();

        // Aunque la contraseña sea correcta, el bloqueo activo impide el login.
        assertThatThrownBy(() -> authenticator.authenticate(
                projectId, "neo@example.com", "secret123", request, response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(ProjectSessionAuthenticator.GENERIC_ERROR);
    }

    @Test
    void lockoutExpiresAndCorrectPasswordWorksAgain() {
        ProjectSessionAuthenticator shortLockAuth = new ProjectSessionAuthenticator(
                userDetailsService, repository, encoder, recordLoginService, eventPublisher,
                new IdentityLoginProperties(2, Duration.ofMillis(1)));
        UUID projectId = UUID.randomUUID();
        ProjectUser user = activeUser(projectId, "neo@example.com", "secret123");
        when(userDetailsService.loadProjectUser(projectId, "neo@example.com"))
                .thenReturn(ProjectUserPrincipal.from(user, AUTHORITIES));
        when(repository.findByProjectIdAndId(projectId, user.getId())).thenReturn(Optional.of(user));

        // 2 fallos bloquean 1ms.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> shortLockAuth.authenticate(
                    projectId, "neo@example.com", "WRONG", request, response))
                    .isInstanceOf(BadCredentialsException.class);
        }
        sleepQuiet(10); // expira el bloqueo

        Authentication result = shortLockAuth.authenticate(
                projectId, "neo@example.com", "secret123", request, response);
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void successfulLoginResetsFailedAttemptCounter() {
        UUID projectId = UUID.randomUUID();
        ProjectUser user = activeUser(projectId, "neo@example.com", "secret123");
        user.recordFailedLogin(Instant.now(), 5, Duration.ofMinutes(15)); // 1 fallo previo
        when(userDetailsService.loadProjectUser(projectId, "neo@example.com"))
                .thenReturn(ProjectUserPrincipal.from(user, AUTHORITIES));
        when(repository.findByProjectIdAndId(projectId, user.getId())).thenReturn(Optional.of(user));

        authenticator.authenticate(projectId, "neo@example.com", "secret123", request, response);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    private void assertAudited(String action, UUID expectedUserId) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo(action);
        assertThat(event.actorType()).isEqualTo("PROJECT_USER");
        assertThat(event.actorId()).isEqualTo(expectedUserId == null ? null : expectedUserId.toString());
    }

    private static void lockNow(ProjectUser user) {
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(now, 5, Duration.ofMinutes(15));
        }
    }

    private static void sleepQuiet(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ProjectUser activeUser(UUID projectId, String email, String password) {
        ProjectUser user = new ProjectUser(projectId, email, new BCryptPasswordEncoder().encode(password), email);
        user.verifyEmail(Instant.parse("2026-01-01T00:00:00Z"));
        // El id es @GeneratedValue (lo asigna JPA al persistir); en test unitario lo
        // fijamos a mano porque el authenticator ya indexa la sesión por user.getId().
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static ProjectUser suspendedUser(UUID projectId, String email, String password) {
        ProjectUser user = activeUser(projectId, email, password);
        user.suspend();
        return user;
    }

    private static ProjectUser pendingUser(UUID projectId, String email, String password) {
        // Sin verifyEmail → queda PENDING_VERIFICATION, emailVerifiedAt null.
        ProjectUser user = new ProjectUser(projectId, email, new BCryptPasswordEncoder().encode(password), email);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
