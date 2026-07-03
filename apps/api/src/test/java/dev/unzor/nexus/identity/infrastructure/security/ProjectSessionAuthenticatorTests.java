package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.application.service.RecordProjectUserLoginService;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSessionAuthenticatorTests {

    private static final List<SimpleGrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_PROJECT_USER"));

    private final ProjectUserUserDetailsService userDetailsService = mock(ProjectUserUserDetailsService.class);
    private final ProjectUserRepository repository = mock(ProjectUserRepository.class);
    private final RecordProjectUserLoginService recordLoginService = mock(RecordProjectUserLoginService.class);
    private final org.springframework.context.ApplicationEventPublisher eventPublisher =
            mock(org.springframework.context.ApplicationEventPublisher.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final ProjectSessionAuthenticator authenticator = new ProjectSessionAuthenticator(
            userDetailsService, repository, encoder, recordLoginService, eventPublisher);

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
        assertThat(result.getAuthorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_PROJECT_USER");
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

        verify(recordLoginService, never()).recordLogin(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertAudited("project_user.login_failed", null);
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

        verify(recordLoginService, never()).recordLogin(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertAudited("project_user.login_failed", user.getId());
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

        verify(recordLoginService, never()).recordLogin(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertAudited("project_user.login_failed", user.getId());
    }

    private void assertAudited(String action, UUID expectedUserId) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo(action);
        assertThat(event.actorType()).isEqualTo("PROJECT_USER");
        assertThat(event.actorId()).isEqualTo(expectedUserId == null ? null : expectedUserId.toString());
    }

    private static ProjectUser activeUser(UUID projectId, String email, String password) {
        ProjectUser user = new ProjectUser(projectId, email, new BCryptPasswordEncoder().encode(password), email);
        user.verifyEmail(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    private static ProjectUser suspendedUser(UUID projectId, String email, String password) {
        ProjectUser user = activeUser(projectId, email, password);
        user.suspend();
        return user;
    }
}
