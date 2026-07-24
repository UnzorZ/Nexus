package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.IdentityLoginProperties;
import dev.unzor.nexus.identity.application.configuration.OidcFederationProperties;
import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.domain.entity.ProjectOidcIdp;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.entity.ProjectUserOidcLink;
import dev.unzor.nexus.identity.domain.exception.OidcFederationException;
import dev.unzor.nexus.identity.infrastructure.security.ProjectSessionAuthenticator;
import dev.unzor.nexus.identity.persistence.repository.ProjectOidcIdpRepository;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserOidcLinkRepository;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.security.OidcFederationCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the orchestration: account resolution, the re-authenticated linking rule,
 * provisioning and the edge cases. The exchange and id_token verification are mocked so the
 * tests focus on the resolution policy; the verifier and exchange services have their own
 * dedicated tests.
 */
class GoogleFederationServiceTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final String SUBJECT = "google-sub-123";
    private static final String EMAIL = "neo@example.com";
    private static final String NONCE = "the-nonce";

    private final ProjectOidcIdpRepository idpRepository = mock(ProjectOidcIdpRepository.class);
    private final ProjectUserOidcLinkRepository linkRepository = mock(ProjectUserOidcLinkRepository.class);
    private final ProjectUserRepository userRepository = mock(ProjectUserRepository.class);
    private final OidcFederationCrypto crypto = mock(OidcFederationCrypto.class);
    private final GoogleStateNonceService stateNonceService = mock(GoogleStateNonceService.class);
    private final GoogleTokenExchangeService tokenExchangeService = mock(GoogleTokenExchangeService.class);
    private final GoogleIdTokenVerifier idTokenVerifier = mock(GoogleIdTokenVerifier.class);
    private final ProjectSessionAuthenticator sessionAuthenticator = mock(ProjectSessionAuthenticator.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final GoogleFederationService service = new GoogleFederationService(
            idpRepository, linkRepository, userRepository, crypto, stateNonceService, tokenExchangeService,
            idTokenVerifier, sessionAuthenticator, passwordEncoder,
            new IdentityLoginProperties(5, Duration.ofMinutes(15)),
            new OidcFederationProperties(null, Duration.ofMinutes(5), Duration.ofMinutes(10)),
            eventPublisher, noopTransactionManager());

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        when(crypto.decrypt(anyString())).thenReturn("secret");
    }

    @Test
    void knownSubjectLogsInDirectlyWithoutReauthentication() {
        ProjectUser linkedUser = activeUser(EMAIL, "hash");
        stubCallback(verified(SUBJECT, EMAIL, NONCE), true, false);
        when(linkRepository.findByProjectIdAndProviderAndSubject(PROJECT_ID, GoogleOidc.PROVIDER, SUBJECT))
                .thenReturn(Optional.of(new ProjectUserOidcLink(PROJECT_ID, linkedUser.getId(), GoogleOidc.PROVIDER, SUBJECT, EMAIL)));
        when(userRepository.findByProjectIdAndId(PROJECT_ID, linkedUser.getId())).thenReturn(Optional.of(linkedUser));

        GoogleLoginOutcome outcome = service.handleCallback(ctx(), "code", "state", null, request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.LoggedIn.class);
        verify(sessionAuthenticator).establishFederatedSession(eq(PROJECT_ID), eq(linkedUser.getId()),
                eq(GoogleOidc.FACTOR), any(Instant.class), any(), any());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void verifiedEmailMatchNeverLinksOrLogsInWithoutReauthentication() {
        // HARD RULE: a verified email that matches an existing account must NOT auto-link.
        ProjectUser existing = activeUser(EMAIL, "hash");
        stubCallback(verified(SUBJECT, EMAIL, NONCE), true, false);
        when(linkRepository.findByProjectIdAndProviderAndSubject(PROJECT_ID, GoogleOidc.PROVIDER, SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.findByProjectIdAndEmailIgnoreCase(PROJECT_ID, EMAIL)).thenReturn(Optional.of(existing));

        GoogleLoginOutcome outcome = service.handleCallback(ctx(), "code", "state", null, request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.LinkRequired.class);
        verify(sessionAuthenticator, never()).establishFederatedSession(any(), any(), any(), any(), any(), any());
        verify(linkRepository, never()).save(any());
        // A pending link ticket was stored so re-authentication can complete the link.
        assertThat(request.getSession().getAttribute(GoogleOidc.LINK_TICKET_SESSION_KEY))
                .isInstanceOf(GoogleLinkTicket.class);
    }

    @Test
    void completeLinkingWithCorrectPasswordCreatesLinkAndLogsIn() {
        ProjectUser existing = activeUser(EMAIL, "stored-hash");
        storeLinkTicket(existing.getId(), SUBJECT, EMAIL, "/continue");
        when(userRepository.findByProjectIdAndId(PROJECT_ID, existing.getId())).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

        GoogleLoginOutcome outcome = service.completeLinking(ctx(), "correct-password", request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.LoggedIn.class);
        ArgumentCaptor<ProjectUserOidcLink> linkCaptor = ArgumentCaptor.forClass(ProjectUserOidcLink.class);
        verify(linkRepository).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getSubject()).isEqualTo(SUBJECT);
        assertThat(linkCaptor.getValue().getProjectUserId()).isEqualTo(existing.getId());
        verify(sessionAuthenticator).establishFederatedSession(eq(PROJECT_ID), eq(existing.getId()),
                eq(GoogleOidc.FACTOR), any(Instant.class), any(), any());
        // Ticket consumed after a successful link.
        assertThat(request.getSession().getAttribute(GoogleOidc.LINK_TICKET_SESSION_KEY)).isNull();
    }

    @Test
    void completeLinkingWithWrongPasswordDoesNotLink() {
        ProjectUser existing = activeUser(EMAIL, "stored-hash");
        storeLinkTicket(existing.getId(), SUBJECT, EMAIL, "/continue");
        when(userRepository.findByProjectIdAndId(PROJECT_ID, existing.getId())).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        GoogleLoginOutcome outcome = service.completeLinking(ctx(), "wrong", request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.FederationError.class)
                .extracting("code").isEqualTo("invalid_credentials");
        verify(linkRepository, never()).save(any());
        verify(sessionAuthenticator, never()).establishFederatedSession(any(), any(), any(), any(), any(), any());
        // The ticket stays so the user may retry, exactly like the MFA pending window.
        assertThat(request.getSession().getAttribute(GoogleOidc.LINK_TICKET_SESSION_KEY)).isNotNull();
    }

    @Test
    void provisioningCreatesActiveVerifiedUserAndLinkWhenEnabled() {
        stubCallback(verified(SUBJECT, EMAIL, NONCE), true, true);
        when(linkRepository.findByProjectIdAndProviderAndSubject(PROJECT_ID, GoogleOidc.PROVIDER, SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.findByProjectIdAndEmailIgnoreCase(PROJECT_ID, EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any(CharSequence.class))).thenReturn("random-hash");
        // Simulate JPA assigning the generated id so the link can reference it.
        when(userRepository.save(any(ProjectUser.class))).thenAnswer(inv -> {
            ProjectUser saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        GoogleLoginOutcome outcome = service.handleCallback(ctx(), "code", "state", null, request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.LoggedIn.class);
        ArgumentCaptor<ProjectUser> userCaptor = ArgumentCaptor.forClass(ProjectUser.class);
        verify(userRepository).save(userCaptor.capture());
        ProjectUser provisioned = userCaptor.getValue();
        assertThat(provisioned.getEmail()).isEqualTo(EMAIL);
        assertThat(provisioned.canAuthenticate()).isTrue(); // ACTIVE
        assertThat(provisioned.isEmailVerified()).isTrue(); // Google proved the email
        verify(linkRepository).save(any());
        verify(sessionAuthenticator).establishFederatedSession(eq(PROJECT_ID), any(), eq(GoogleOidc.FACTOR),
                any(Instant.class), any(), any());
    }

    @Test
    void noProvisioningAndNoMatchReturnsAccountNotFound() {
        stubCallback(verified(SUBJECT, EMAIL, NONCE), true, false);
        when(linkRepository.findByProjectIdAndProviderAndSubject(PROJECT_ID, GoogleOidc.PROVIDER, SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.findByProjectIdAndEmailIgnoreCase(PROJECT_ID, EMAIL)).thenReturn(Optional.empty());

        GoogleLoginOutcome outcome = service.handleCallback(ctx(), "code", "state", null, request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.FederationError.class)
                .extracting("code").isEqualTo("account_not_found");
        verify(userRepository, never()).save(any());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void unverifiedGoogleEmailIsRejected() {
        stubFailingVerify(new OidcFederationException("email_not_verified", "no"));

        GoogleLoginOutcome outcome = service.handleCallback(ctx(), "code", "state", null, request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.FederationError.class)
                .extracting("code").isEqualTo("email_not_verified");
        verify(sessionAuthenticator, never()).establishFederatedSession(any(), any(), any(), any(), any(), any());
    }

    @Test
    void mismatchedNonceIsRejected() {
        // The session ticket carries the nonce sent to Google; the id_token must echo it.
        when(stateNonceService.consume(eq(PROJECT_ID), anyString(), any())).thenReturn(
                new OidcLoginState("state", NONCE, PROJECT_ID, "/continue", Instant.now(), Instant.now().plusSeconds(300)));
        when(tokenExchangeService.exchange(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GoogleTokenSet("id-token", "access", "Bearer", "openid"));
        when(idTokenVerifier.verify(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(verified(SUBJECT, EMAIL, "tampered-nonce"));
        when(idpRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(config(true, false)));

        GoogleLoginOutcome outcome = service.handleCallback(ctx(), "code", "state", null, request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.FederationError.class)
                .extracting("code").isEqualTo("invalid_nonce");
    }

    @Test
    void invalidStateFromCallbackIsMappedToAnError() {
        when(stateNonceService.consume(eq(PROJECT_ID), eq("state"), any())).thenThrow(
                new OidcFederationException("invalid_state", "replay"));

        GoogleLoginOutcome outcome = service.handleCallback(ctx(), "code", "state", null, request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.FederationError.class)
                .extracting("code").isEqualTo("invalid_state");
    }

    @Test
    void googleErrorParameterIsMappedToProviderError() {
        stubCallback(verified(SUBJECT, EMAIL, NONCE), true, false);

        GoogleLoginOutcome outcome = service.handleCallback(ctx(), "code", "state", "access_denied", request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.FederationError.class)
                .extracting("code").isEqualTo("provider_error");
    }

    @Test
    void subjectAlreadyLinkedToAnotherAccountBlocksLinkCreation() {
        ProjectUser existing = activeUser(EMAIL, "stored-hash");
        storeLinkTicket(existing.getId(), "other-subject", EMAIL, "/continue");
        when(userRepository.findByProjectIdAndId(PROJECT_ID, existing.getId())).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("correct", "stored-hash")).thenReturn(true);
        when(linkRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk_oidc_link_project_provider_subject"));

        GoogleLoginOutcome outcome = service.completeLinking(ctx(), "correct", request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.FederationError.class)
                .extracting("code").isEqualTo("already_linked");
        verify(sessionAuthenticator, never()).establishFederatedSession(any(), any(), any(), any(), any(), any());
    }

    // ---- helpers ----

    private void stubCallback(VerifiedGoogleIdToken verified, boolean enabled, boolean autoProvision) {
        when(stateNonceService.consume(eq(PROJECT_ID), anyString(), any())).thenReturn(
                new OidcLoginState("state", verified.nonce(), PROJECT_ID, "/continue", Instant.now(), Instant.now().plusSeconds(300)));
        when(tokenExchangeService.exchange(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GoogleTokenSet("id-token", "access", "Bearer", "openid"));
        when(idTokenVerifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(verified);
        when(idpRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(config(enabled, autoProvision)));
    }

    private void stubFailingVerify(OidcFederationException failure) {
        when(stateNonceService.consume(eq(PROJECT_ID), anyString(), any())).thenReturn(
                new OidcLoginState("state", NONCE, PROJECT_ID, "/continue", Instant.now(), Instant.now().plusSeconds(300)));
        when(tokenExchangeService.exchange(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GoogleTokenSet("id-token", "access", "Bearer", "openid"));
        when(idTokenVerifier.verify(anyString(), anyString(), anyString(), anyString())).thenThrow(failure);
        when(idpRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(config(true, false)));
    }

    private void storeLinkTicket(UUID userId, String subject, String email, String continueUrl) {
        GoogleLinkTicket ticket = new GoogleLinkTicket(PROJECT_ID, userId, GoogleOidc.PROVIDER, subject, email,
                "Neo", continueUrl, Instant.now(), Instant.now().plusSeconds(600));
        request.getSession().setAttribute(GoogleOidc.LINK_TICKET_SESSION_KEY, ticket);
    }

    private static ProjectAuthenticationContext ctx() {
        return new ProjectAuthenticationContext(PROJECT_ID, "my-project");
    }

    private static VerifiedGoogleIdToken verified(String subject, String email, String nonce) {
        return new VerifiedGoogleIdToken(subject, email, true, "https://accounts.google.com", "cid",
                nonce, "Neo", "Neo", "Anderson");
    }

    private static ProjectOidcIdp config(boolean enabled, boolean autoProvision) {
        return new ProjectOidcIdp(PROJECT_ID, "https://accounts.google.com", "cid", "enc",
                "openid email profile", enabled, autoProvision);
    }

    private static ProjectUser activeUser(String email, String passwordHash) {
        ProjectUser user = new ProjectUser(PROJECT_ID, email, passwordHash, email);
        user.verifyEmail(Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    /** A transaction manager with no backing resource, so unit tests can run the lambdas. */
    private static PlatformTransactionManager noopTransactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected boolean isExistingTransaction(Object transaction) {
                return false;
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                // no-op
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                // no-op
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                // no-op
            }
        };
    }
}
