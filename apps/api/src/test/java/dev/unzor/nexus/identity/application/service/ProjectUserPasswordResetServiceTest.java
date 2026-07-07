package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.IdentityEmailProperties;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.exception.InvalidPasswordResetTokenException;
import dev.unzor.nexus.identity.domain.exception.WeakPasswordException;
import dev.unzor.nexus.identity.infrastructure.IdentityTokens;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.OutboundTransactionalEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectUserPasswordResetServiceTest {

    private final ProjectUserRepository repository = mock(ProjectUserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final EndUserLinkBuilder linkBuilder = mock(EndUserLinkBuilder.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final ProjectUserSessionService sessionService = mock(ProjectUserSessionService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final IdentityEmailProperties emailProperties =
            new IdentityEmailProperties(Duration.ofHours(24), Duration.ofHours(1));
    private final ProjectUserPasswordResetService service = new ProjectUserPasswordResetService(
            repository, encoder, linkBuilder, projectLookupService, sessionService, eventPublisher, emailProperties);

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(repository.save(any(ProjectUser.class))).thenAnswer(i -> i.getArgument(0));
        when(linkBuilder.passwordResetLink(eq("acme"), any(String.class)))
                .thenReturn("https://api.example.test/p/acme/password-reset/confirm?token=x");
        when(projectLookupService.requireSlug(projectId)).thenReturn("acme");
    }

    @Test
    void requestResetIgnoresUnverifiedAccount() {
        ProjectUser user = pendingUser(); // not verified
        when(repository.findByProjectIdAndEmailIgnoreCase(projectId, user.getEmail()))
                .thenReturn(Optional.of(user));

        service.requestReset(projectId, user.getEmail());

        verify(repository, never()).save(any(ProjectUser.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestResetSendsLinkForVerifiedAccount() {
        ProjectUser user = verifiedUser();
        when(repository.findByProjectIdAndEmailIgnoreCase(projectId, user.getEmail()))
                .thenReturn(Optional.of(user));

        service.requestReset(projectId, user.getEmail());

        assertThat(user.getPasswordResetTokenHash()).isNotBlank();
        ArgumentCaptor<OutboundTransactionalEmail> captor = ArgumentCaptor.forClass(OutboundTransactionalEmail.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().recipientEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void confirmSetsPasswordRevokesSessionsAndBumpsAuthzVersion() {
        ProjectUser user = verifiedUser();
        String oldHash = user.getPasswordHash();
        String raw = "raw-reset-token";
        user.issuePasswordReset(IdentityTokens.hash(raw), Instant.now().plusSeconds(3600));
        when(repository.findByProjectIdAndPasswordResetTokenHash(projectId, IdentityTokens.hash(raw)))
                .thenReturn(Optional.of(user));

        service.confirm(projectId, raw, "newSecret1");

        assertThat(user.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(user.getPasswordResetTokenHash()).isNull(); // single-use cleared
        assertThat(user.getAuthzVersion()).isEqualTo(1L); // bumped → introspection kills old tokens
        verify(sessionService).revokeAll(any()); // sessions killed
    }

    @Test
    void confirmExpiredTokenThrows() {
        ProjectUser user = verifiedUser();
        String raw = "expired";
        user.issuePasswordReset(IdentityTokens.hash(raw), Instant.now().minusSeconds(60));
        when(repository.findByProjectIdAndPasswordResetTokenHash(projectId, IdentityTokens.hash(raw)))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.confirm(projectId, raw, "newSecret1"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void confirmUnknownTokenThrows() {
        when(repository.findByProjectIdAndPasswordResetTokenHash(eq(projectId), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(projectId, "nope", "newSecret1"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void confirmRejectsWeakPassword() {
        ProjectUser user = verifiedUser();
        String raw = "raw-reset-token";
        user.issuePasswordReset(IdentityTokens.hash(raw), Instant.now().plusSeconds(3600));
        when(repository.findByProjectIdAndPasswordResetTokenHash(projectId, IdentityTokens.hash(raw)))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.confirm(projectId, raw, "short"))
                .isInstanceOf(WeakPasswordException.class);
        verify(sessionService, never()).revokeAll(any());
    }

    private ProjectUser pendingUser() {
        return new ProjectUser(projectId, "neo@example.com", encoder.encode("secret123"), "Neo");
    }

    private ProjectUser verifiedUser() {
        ProjectUser user = pendingUser();
        user.verifyEmail(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }
}
