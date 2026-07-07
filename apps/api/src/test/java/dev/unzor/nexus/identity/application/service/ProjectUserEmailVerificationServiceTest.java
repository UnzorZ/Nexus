package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.IdentityEmailProperties;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.exception.InvalidEmailVerificationTokenException;
import dev.unzor.nexus.identity.infrastructure.IdentityTokens;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.OutboundTransactionalEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

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

class ProjectUserEmailVerificationServiceTest {

    private final ProjectUserRepository repository = mock(ProjectUserRepository.class);
    private final EndUserLinkBuilder linkBuilder = mock(EndUserLinkBuilder.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final IdentityEmailProperties emailProperties =
            new IdentityEmailProperties(Duration.ofHours(24), Duration.ofHours(1));
    private final ProjectUserEmailVerificationService service = new ProjectUserEmailVerificationService(
            repository, linkBuilder, projectLookupService, eventPublisher, emailProperties);

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(repository.save(any(ProjectUser.class))).thenAnswer(i -> i.getArgument(0));
        when(linkBuilder.verifyEmailLink(eq("acme"), any(String.class)))
                .thenReturn("https://api.example.test/p/acme/verify-email?token=x");
        when(projectLookupService.requireSlug(projectId)).thenReturn("acme");
    }

    @Test
    void issueAndSendStampsHashedTokenAndPublishesEmail() {
        ProjectUser user = pendingUser();

        service.issueAndSend(user);

        assertThat(user.getEmailVerificationTokenHash()).isNotBlank();
        assertThat(user.getEmailVerificationExpiresAt()).isNotNull();
        ArgumentCaptor<OutboundTransactionalEmail> captor = ArgumentCaptor.forClass(OutboundTransactionalEmail.class);
        verify(eventPublisher).publishEvent(captor.capture());
        OutboundTransactionalEmail email = captor.getValue();
        assertThat(email.projectId()).isEqualTo(projectId);
        assertThat(email.recipientEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void verifyConsumesTokenAndActivatesUser() {
        ProjectUser user = pendingUser();
        String raw = "raw-token-xyz";
        user.issueEmailVerification(IdentityTokens.hash(raw), Instant.now().plusSeconds(3600));
        when(repository.findByProjectIdAndEmailVerificationTokenHash(projectId, IdentityTokens.hash(raw)))
                .thenReturn(Optional.of(user));

        service.verify(projectId, raw);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerificationTokenHash()).isNull(); // single-use cleared
    }

    @Test
    void verifyUnknownTokenThrows() {
        when(repository.findByProjectIdAndEmailVerificationTokenHash(eq(projectId), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(projectId, "nope"))
                .isInstanceOf(InvalidEmailVerificationTokenException.class);
    }

    @Test
    void verifyExpiredTokenThrows() {
        ProjectUser user = pendingUser();
        String raw = "expired";
        user.issueEmailVerification(IdentityTokens.hash(raw), Instant.now().minusSeconds(60)); // expired
        when(repository.findByProjectIdAndEmailVerificationTokenHash(projectId, IdentityTokens.hash(raw)))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verify(projectId, raw))
                .isInstanceOf(InvalidEmailVerificationTokenException.class);
    }

    @Test
    void resendIsNoOpWhenAlreadyVerified() {
        ProjectUser user = pendingUser();
        user.verifyEmail(Instant.now()); // already verified
        when(repository.findByProjectIdAndEmailIgnoreCase(projectId, user.getEmail()))
                .thenReturn(Optional.of(user));

        service.resend(projectId, user.getEmail());

        verify(repository, never()).save(any(ProjectUser.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    private ProjectUser pendingUser() {
        return new ProjectUser(projectId, "neo@example.com", "hash", "Neo");
    }
}
