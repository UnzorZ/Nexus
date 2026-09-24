package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.IdentityLoginProperties;
import dev.unzor.nexus.identity.application.configuration.OidcFederationProperties;
import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.domain.entity.ProjectOidcIdp;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.entity.ProjectUserOidcLink;
import dev.unzor.nexus.identity.infrastructure.security.MfaPendingTicket;
import dev.unzor.nexus.identity.infrastructure.security.ProjectSessionAuthenticator;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserUserDetailsService;
import dev.unzor.nexus.identity.persistence.repository.ProjectOidcIdpRepository;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserOidcLinkRepository;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRecoveryCodeRepository;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.security.Base32;
import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import dev.unzor.nexus.shared.security.OidcFederationCrypto;
import dev.unzor.nexus.shared.security.TotpCrypto;
import dev.unzor.nexus.shared.security.TotpGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
 * Contrato del gate de MFA en el login federado Google (corrección de seguridad): un
 * usuario con TOTP inscrito que entra por Google NO obtiene sesión autenticada — se fija
 * el mismo ticket efímero que el flujo de contraseña y el segundo factor se completa por
 * {@code POST /api/p/{slug}/login/mfa}, que establece la sesión con el factor primario
 * Google + TOTP. El camino feliz sin MFA no cambia.
 */
class GoogleFederatedMfaLoginTests {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final String SUBJECT = "google-sub-mfa";
    private static final String EMAIL = "mfa-user@example.com";
    private static final String NONCE = "the-nonce";
    private static final List<SimpleGrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_PROJECT_USER"));

    // -- harness del servicio federado (como GoogleFederationServiceTest) --
    private final ProjectOidcIdpRepository idpRepository = mock(ProjectOidcIdpRepository.class);
    private final ProjectUserOidcLinkRepository linkRepository = mock(ProjectUserOidcLinkRepository.class);
    private final ProjectUserRepository userRepository = mock(ProjectUserRepository.class);
    private final OidcFederationCrypto crypto = mock(OidcFederationCrypto.class);
    private final GoogleStateNonceService stateNonceService = mock(GoogleStateNonceService.class);
    private final GoogleTokenExchangeService tokenExchangeService = mock(GoogleTokenExchangeService.class);
    private final GoogleIdTokenVerifier idTokenVerifier = mock(GoogleIdTokenVerifier.class);
    private final ProjectSessionAuthenticator federatedSessionAuthenticator = mock(ProjectSessionAuthenticator.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    // -- harness del autenticador de sesión (como ProjectSessionAuthenticatorTests) para
    // completar el segundo factor de verdad (TOTP real sobre el secret descifrado) --
    private final ProjectUserUserDetailsService userDetailsService = mock(ProjectUserUserDetailsService.class);
    private final ProjectUserRecoveryCodeRepository recoveryCodeRepository = mock(ProjectUserRecoveryCodeRepository.class);
    private final RecordProjectUserLoginService recordLoginService = mock(RecordProjectUserLoginService.class);
    private final TotpCrypto totpCrypto = mock(TotpCrypto.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);

    private final GoogleFederationService federationService = new GoogleFederationService(
            idpRepository, linkRepository, userRepository, crypto, stateNonceService, tokenExchangeService,
            idTokenVerifier, federatedSessionAuthenticator, passwordEncoder,
            new IdentityLoginProperties(5, Duration.ofMinutes(15)),
            new OidcFederationProperties(null, Duration.ofMinutes(5), Duration.ofMinutes(10)),
            eventPublisher, noopTransactionManager());

    private final ProjectSessionAuthenticator sessionAuthenticator = new ProjectSessionAuthenticator(
            userDetailsService, userRepository, recoveryCodeRepository, new BCryptPasswordEncoder(),
            recordLoginService, eventPublisher, new IdentityLoginProperties(5, Duration.ofMinutes(15)),
            totpCrypto, projectLookupService);

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        when(crypto.decrypt(anyString())).thenReturn("secret");
    }

    @Test
    void googleLoginOfLinkedUserWithTotpEstablishesNoSessionAndRequiresMfa() {
        // (a) Primer factor federado + TOTP inscrito → SIN sesión autenticada; el ticket
        // queda aparcado en la sesión anónima (índice del ProjectUser incluido) y el
        // resultado pide el segundo factor.
        ProjectUser linkedUser = totpUser();
        stubLinkedUser(linkedUser);

        GoogleLoginOutcome outcome = federationService.handleCallback(ctx(), "code", "state", null, request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.MfaRequired.class);
        assertThat(((GoogleLoginOutcome.MfaRequired) outcome).continueUrl()).isEqualTo("/continue");
        verify(federatedSessionAuthenticator, never())
                .establishFederatedSession(any(), any(), any(), any(), any(), any());
        var session = request.getSession(false);
        assertThat(session).isNotNull();
        // Sin SecurityContext: el AS no puede reanudar /oauth2/authorize en la ventana.
        assertThat(session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        // Ticket aparcado con factor primario google, mismo attr que el flujo de contraseña.
        assertThat(session.getAttribute(NexusSessionAttributes.MFA_PENDING)).isInstanceOf(MfaPendingTicket.class);
        MfaPendingTicket ticket = (MfaPendingTicket) session.getAttribute(NexusSessionAttributes.MFA_PENDING);
        assertThat(ticket.userId()).isEqualTo(linkedUser.getId());
        assertThat(ticket.projectId()).isEqualTo(PROJECT_ID);
        assertThat(ticket.primaryFactor()).isEqualTo(MfaPendingTicket.FACTOR_GOOGLE);
        assertThat(ticket.expiresAt()).isAfter(Instant.now());
        // Marcadores de indexado para que el archivado del proyecto encuentre la sesión.
        assertThat(session.getAttribute(NexusSessionAttributes.PROJECT_USER_ID))
                .isEqualTo(linkedUser.getId().toString());
        assertThat(session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
                .isEqualTo(NexusSessionAttributes.projectUserIndexValue(linkedUser.getId()));
    }

    @Test
    void completingMfaAfterFederatedTicketEstablishesSessionWithGoogleAndTotpFactors() {
        // (b) Completar el TOTP tras un ticket federado → sesión con factor primario
        // google (FACTOR_google) + segundo factor TOTP (FACTOR_TOTP); auditoría como
        // authMethod google y mfa=true.
        ProjectUser user = totpUser();
        byte[] secret = TotpGenerator.generateSecret();
        user.storeTotpSecret(Base32.encode(secret));
        Instant verifiedAt = Instant.now().minusSeconds(60);
        request.getSession().setAttribute(NexusSessionAttributes.MFA_PENDING,
                new MfaPendingTicket(user.getId(), PROJECT_ID, verifiedAt,
                        Instant.now().plusSeconds(300), MfaPendingTicket.FACTOR_GOOGLE));
        when(userRepository.findByProjectIdAndId(PROJECT_ID, user.getId())).thenReturn(Optional.of(user));
        when(userDetailsService.loadProjectUser(PROJECT_ID, user.getEmail()))
                .thenReturn(ProjectUserPrincipal.from(user, AUTHORITIES));
        when(totpCrypto.decrypt(anyString())).thenReturn(Base32.encode(secret));
        String code = TotpGenerator.generate(secret, Instant.now().getEpochSecond());

        Authentication result = sessionAuthenticator.completeMfaAuthentication(
                PROJECT_ID, code, request, response);

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getAuthorities()).extracting(a -> a.getAuthority())
                .contains("FACTOR_google", "FACTOR_TOTP");
        // El ticket se consumió y la sesión es real (indexada, con id público revocable).
        var session = request.getSession(false);
        assertThat(session.getAttribute(NexusSessionAttributes.MFA_PENDING)).isNull();
        assertThat(session.getAttribute(NexusSessionAttributes.PROJECT_USER_ID))
                .isEqualTo(user.getId().toString());
        assertThat(session.getAttribute(NexusSessionAttributes.SESSION_PUBLIC_ID)).isNotNull();
        verify(recordLoginService).recordLogin(PROJECT_ID, user.getId());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo("project_user.login_succeeded");
        assertThat(event.metadata()).containsEntry("authMethod", MfaPendingTicket.FACTOR_GOOGLE)
                .containsEntry("mfa", "true");
    }

    @Test
    void googleLoginOfLinkedUserWithoutTotpStillLogsInDirectly() {
        // (c) Regresión del camino feliz: sin TOTP inscrito, el login federado sigue
        // estableciendo la sesión directamente (LoggedIn) sin ticket MFA.
        ProjectUser linkedUser = activeUser();
        stubLinkedUser(linkedUser);

        GoogleLoginOutcome outcome = federationService.handleCallback(ctx(), "code", "state", null, request, response);

        assertThat(outcome).isInstanceOf(GoogleLoginOutcome.LoggedIn.class);
        verify(federatedSessionAuthenticator).establishFederatedSession(
                eq(PROJECT_ID), eq(linkedUser.getId()), eq(GoogleOidc.FACTOR),
                any(Instant.class), any(), any());
        assertThat(request.getSession(false)
                .getAttribute(NexusSessionAttributes.MFA_PENDING)).isNull();
    }

    @Test
    void completingMfaAfterFederatedTicketWithWrongCodeDoesNotAuthenticate() {
        // (d) Complemento del (b): un código erróneo tras un ticket federado NO autentica
        // (cuenta hacia el lockout) y el ticket sobrevive para reintentar, igual que en el
        // flujo de contraseña.
        ProjectUser user = totpUser();
        byte[] secret = TotpGenerator.generateSecret();
        user.storeTotpSecret(Base32.encode(secret));
        request.getSession().setAttribute(NexusSessionAttributes.MFA_PENDING,
                new MfaPendingTicket(user.getId(), PROJECT_ID, Instant.now(),
                        Instant.now().plusSeconds(300), MfaPendingTicket.FACTOR_GOOGLE));
        when(userRepository.findByProjectIdAndId(PROJECT_ID, user.getId())).thenReturn(Optional.of(user));
        when(totpCrypto.decrypt(anyString())).thenReturn(Base32.encode(secret));
        // Código válido de OTRO secret → no valida ni por TOTP ni por recovery.
        byte[] otherSecret = TotpGenerator.generateSecret();
        String wrongCode = TotpGenerator.generate(otherSecret, Instant.now().getEpochSecond());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sessionAuthenticator.completeMfaAuthentication(
                        PROJECT_ID, wrongCode, request, response))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

        assertThat(request.getSession(false).getAttribute(NexusSessionAttributes.MFA_PENDING)).isNotNull();
        verify(recordLoginService, never()).recordLogin(any(), any());
    }

    // ---- helpers ----

    private void stubLinkedUser(ProjectUser user) {
        stubCallback(verified(SUBJECT, EMAIL, NONCE));
        when(linkRepository.findByProjectIdAndProviderAndSubject(PROJECT_ID, GoogleOidc.PROVIDER, SUBJECT))
                .thenReturn(Optional.of(new ProjectUserOidcLink(
                        PROJECT_ID, user.getId(), GoogleOidc.PROVIDER, SUBJECT, EMAIL)));
        when(userRepository.findByProjectIdAndId(PROJECT_ID, user.getId())).thenReturn(Optional.of(user));
    }

    private void stubCallback(VerifiedGoogleIdToken verified) {
        when(stateNonceService.consume(eq(PROJECT_ID), anyString(), any())).thenReturn(
                new OidcLoginState("state", verified.nonce(), PROJECT_ID, "/continue",
                        Instant.now(), Instant.now().plusSeconds(300)));
        when(tokenExchangeService.exchange(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GoogleTokenSet("id-token", "access", "Bearer", "openid"));
        when(idTokenVerifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(verified);
        when(idpRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(config()));
    }

    /** Usuario ACTIVE, email verificado y con TOTP inscrito (isMfaEnabled = true). */
    private static ProjectUser totpUser() {
        ProjectUser user = activeUser();
        user.storeTotpSecret("encrypted-secret");
        user.enableTotp(Instant.now());
        return user;
    }

    private static ProjectUser activeUser() {
        ProjectUser user = new ProjectUser(PROJECT_ID, EMAIL, "hash", EMAIL);
        user.verifyEmail(Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static ProjectAuthenticationContext ctx() {
        return new ProjectAuthenticationContext(PROJECT_ID, "my-project");
    }

    private static VerifiedGoogleIdToken verified(String subject, String email, String nonce) {
        return new VerifiedGoogleIdToken(subject, email, true, "https://accounts.google.com", "cid",
                nonce, "Neo", "Neo", "Anderson");
    }

    private static ProjectOidcIdp config() {
        return new ProjectOidcIdp(PROJECT_ID, "https://accounts.google.com", "cid", "enc",
                "openid email profile", true, false);
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
