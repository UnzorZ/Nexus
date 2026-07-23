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
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.security.OidcFederationCrypto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates the Google OIDC authorization-code login for {@code ProjectUser} accounts:
 * building the authorization request, handling the callback, verifying the id_token, and
 * resolving the identity against the project users.
 *
 * <p><b>Account linking is the security-critical rule:</b> a verified Google identity whose
 * email matches an existing account is NEVER merged or logged in by that match alone. The
 * service only links (and logs in) when the subject is already linked, or after the user
 * re-authenticates with the account password (see {@link #completeLinking}). A Google
 * identity whose email matches no account may provision a new account, but only when the
 * project opted into {@code autoProvision} — provisioning never touches an existing row.</p>
 *
 * <p>The outbound token exchange runs outside any database transaction; only the account
 * resolution and writes run inside a transaction so provisioning (user + link) is atomic.</p>
 */
@Service
public class GoogleFederationService {

    private static final Logger log = LoggerFactory.getLogger(GoogleFederationService.class);
    private static final int DISPLAY_NAME_MAX = 120;

    private final ProjectOidcIdpRepository idpRepository;
    private final ProjectUserOidcLinkRepository linkRepository;
    private final ProjectUserRepository userRepository;
    private final OidcFederationCrypto crypto;
    private final GoogleStateNonceService stateNonceService;
    private final GoogleTokenExchangeService tokenExchangeService;
    private final GoogleIdTokenVerifier idTokenVerifier;
    private final ProjectSessionAuthenticator sessionAuthenticator;
    private final PasswordEncoder passwordEncoder;
    private final IdentityLoginProperties loginProperties;
    private final OidcFederationProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    public GoogleFederationService(
            ProjectOidcIdpRepository idpRepository,
            ProjectUserOidcLinkRepository linkRepository,
            ProjectUserRepository userRepository,
            OidcFederationCrypto crypto,
            GoogleStateNonceService stateNonceService,
            GoogleTokenExchangeService tokenExchangeService,
            GoogleIdTokenVerifier idTokenVerifier,
            ProjectSessionAuthenticator sessionAuthenticator,
            PasswordEncoder passwordEncoder,
            IdentityLoginProperties loginProperties,
            OidcFederationProperties properties,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager
    ) {
        this.idpRepository = idpRepository;
        this.linkRepository = linkRepository;
        this.userRepository = userRepository;
        this.crypto = crypto;
        this.stateNonceService = stateNonceService;
        this.tokenExchangeService = tokenExchangeService;
        this.idTokenVerifier = idTokenVerifier;
        this.sessionAuthenticator = sessionAuthenticator;
        this.passwordEncoder = passwordEncoder;
        this.loginProperties = loginProperties;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Builds the Google authorization URL, storing a fresh state+nonce in the session.
     * Throws {@link OidcFederationException} if federation is not configured or disabled.
     */
    public String buildAuthorizationUrl(ProjectAuthenticationContext ctx, String continueUrl, HttpServletRequest request) {
        ProjectOidcIdp config = requireEnabledConfig(ctx.projectId());
        OidcLoginState ticket = stateNonceService.issue(ctx.projectId(), continueUrl, request.getSession());
        return new GoogleAuthorizationUrl(properties.google(), config, ticket, redirectUri(ctx, request)).toUrl();
    }

    /**
     * Handles the Google callback: validates the state, exchanges the code, verifies the
     * id_token, checks the nonce, and resolves the account. Never throws for expected flow
     * outcomes; every failure is returned as {@link GoogleLoginOutcome.FederationError}.
     */
    public GoogleLoginOutcome handleCallback(
            ProjectAuthenticationContext ctx, String code, String state, String error,
            HttpServletRequest request, HttpServletResponse response
    ) {
        HttpSession session = request.getSession();
        OidcLoginState ticket;
        try {
            ticket = stateNonceService.consume(ctx.projectId(), state, session);
        } catch (OidcFederationException exception) {
            return new GoogleLoginOutcome.FederationError(exception.code());
        }
        if (StringUtils.hasText(error)) {
            return new GoogleLoginOutcome.FederationError("provider_error");
        }

        ProjectOidcIdp config;
        VerifiedGoogleIdToken verified;
        try {
            config = requireEnabledConfig(ctx.projectId());
            GoogleTokenSet tokens = tokenExchangeService.exchange(
                    code, redirectUri(ctx, request), config.getClientId(),
                    crypto.decrypt(config.getClientSecretEnc()));
            verified = idTokenVerifier.verify(
                    tokens.idToken(), properties.google().issuer(),
                    properties.google().alternateIssuer(), config.getClientId());
            if (!Objects.equals(verified.nonce(), ticket.nonce())) {
                return new GoogleLoginOutcome.FederationError("invalid_nonce");
            }
        } catch (OidcFederationException exception) {
            return new GoogleLoginOutcome.FederationError(exception.code());
        }

        return resolveAndLogin(ctx, verified, ticket.continueUrl(), request, response);
    }

    /**
     * Completes a federated login that required re-authentication: verifies the account
     * password, and only then creates the link and establishes the session. The link is
     * never created on a wrong password.
     */
    public GoogleLoginOutcome completeLinking(
            ProjectAuthenticationContext ctx, String password,
            HttpServletRequest request, HttpServletResponse response
    ) {
        HttpSession session = request.getSession(false);
        Object raw = session == null ? null : session.getAttribute(GoogleOidc.LINK_TICKET_SESSION_KEY);
        if (!(raw instanceof GoogleLinkTicket ticket) || !Objects.equals(ticket.projectId(), ctx.projectId())) {
            return new GoogleLoginOutcome.FederationError("link_expired");
        }
        Instant now = Instant.now();
        if (ticket.expiresAt().isBefore(now)) {
            session.removeAttribute(GoogleOidc.LINK_TICKET_SESSION_KEY);
            return new GoogleLoginOutcome.FederationError("link_expired");
        }

        return transaction.execute(status -> {
            ProjectUser user = userRepository.findByProjectIdAndId(ctx.projectId(), ticket.projectUserId())
                    .orElse(null);
            if (user == null || !user.canAuthenticate()) {
                session.removeAttribute(GoogleOidc.LINK_TICKET_SESSION_KEY);
                return new GoogleLoginOutcome.FederationError("link_expired");
            }
            if (user.isLocked(now) || !passwordEncoder.matches(password, user.getPasswordHash())) {
                recordFailedLoginBestEffort(user, now);
                return new GoogleLoginOutcome.FederationError("invalid_credentials");
            }

            // Password proven: create the link, then establish the federated session.
            if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
                user.resetFailedLogins();
                userRepository.save(user);
            }
            try {
                linkRepository.save(new ProjectUserOidcLink(
                        ctx.projectId(), user.getId(), GoogleOidc.PROVIDER, ticket.subject(), ticket.email()));
            } catch (DataIntegrityViolationException exception) {
                // The subject is already linked (to this or another account) — do not log in.
                log.warn("Google link rejected (already linked): projectId={}, subject={}",
                        ctx.projectId(), ticket.subject());
                return new GoogleLoginOutcome.FederationError("already_linked");
            }
            session.removeAttribute(GoogleOidc.LINK_TICKET_SESSION_KEY);
            eventPublisher.publishEvent(AuditEvent.byProjectUser(
                    ctx.projectId(), "project_user.google_linked", "project_user",
                    Objects.toString(user.getId(), null), user.getId(),
                    Map.of("email", user.getEmail(), "subject", ticket.subject())));
            sessionAuthenticator.establishFederatedSession(
                    ctx.projectId(), user.getId(), GoogleOidc.FACTOR, ticket.authenticatedAt(), request, response);
            return new GoogleLoginOutcome.LoggedIn(ticket.continueUrl());
        });
    }

    /** Account resolution inside a transaction: linked login, link-required, or provisioning. */
    private GoogleLoginOutcome resolveAndLogin(
            ProjectAuthenticationContext ctx, VerifiedGoogleIdToken verified, String continueUrl,
            HttpServletRequest request, HttpServletResponse response
    ) {
        return transaction.execute(status -> {
            UUID projectId = ctx.projectId();
            // 1. Already linked: log the owner in directly.
            var existingLink = linkRepository
                    .findByProjectIdAndProviderAndSubject(projectId, GoogleOidc.PROVIDER, verified.subject());
            if (existingLink.isPresent()) {
                return loginLinked(projectId, existingLink.get().getProjectUserId(), continueUrl, request, response);
            }

            // 2. Email matches an existing account, but the Google subject is not linked.
            //    HARD RULE: never merge on an email match. Require re-authentication.
            var match = userRepository.findByProjectIdAndEmailIgnoreCase(projectId, verified.email());
            if (match.isPresent()) {
                return promptForLinking(projectId, match.get(), verified, continueUrl, request.getSession());
            }

            // 3. No existing account: provision one only if the project opted in.
            ProjectOidcIdp config = idpRepository.findByProjectId(projectId).orElse(null);
            if (config == null || !config.isAutoProvision()) {
                return new GoogleLoginOutcome.FederationError("account_not_found");
            }
            return provisionAndLogin(projectId, verified, continueUrl, request, response);
        });
    }

    private GoogleLoginOutcome loginLinked(
            UUID projectId, UUID userId, String continueUrl,
            HttpServletRequest request, HttpServletResponse response
    ) {
        ProjectUser user = userRepository.findByProjectIdAndId(projectId, userId).orElse(null);
        if (user == null || !user.canAuthenticate()) {
            return new GoogleLoginOutcome.FederationError("account_unavailable");
        }
        sessionAuthenticator.establishFederatedSession(
                projectId, userId, GoogleOidc.FACTOR, Instant.now(), request, response);
        return new GoogleLoginOutcome.LoggedIn(continueUrl);
    }

    private GoogleLoginOutcome promptForLinking(
            UUID projectId, ProjectUser user, VerifiedGoogleIdToken verified,
            String continueUrl, HttpSession session
    ) {
        if (!user.canAuthenticate()) {
            return new GoogleLoginOutcome.FederationError("account_unavailable");
        }
        GoogleLinkTicket ticket = new GoogleLinkTicket(
                projectId, user.getId(), GoogleOidc.PROVIDER, verified.subject(), verified.email(),
                verified.name(), continueUrl, Instant.now(), Instant.now().plus(properties.linkTicketTtl()));
        session.setAttribute(GoogleOidc.LINK_TICKET_SESSION_KEY, ticket);
        eventPublisher.publishEvent(AuditEvent.byProjectUser(
                projectId, "project_user.google_link_required", "project_user",
                Objects.toString(user.getId(), null), user.getId(),
                Map.of("email", user.getEmail(), "subject", verified.subject())));
        return new GoogleLoginOutcome.LinkRequired(user.getEmail(), verified.name());
    }

    private GoogleLoginOutcome provisionAndLogin(
            UUID projectId, VerifiedGoogleIdToken verified, String continueUrl,
            HttpServletRequest request, HttpServletResponse response
    ) {
        String email = verified.email().trim();
        String displayName = chooseDisplayName(verified, email);
        // A provisioned account has no password the user knows: store a random, unusable hash
        // (satisfies the NOT NULL column) so password login is impossible for this account.
        String passwordHash = passwordEncoder.encode(randomSecret());
        ProjectUser user = new ProjectUser(projectId, email, passwordHash, displayName);
        user.verifyEmail(Instant.now()); // Google proved the email → ACTIVE + verified.
        ProjectUser saved = userRepository.save(user);
        linkRepository.save(new ProjectUserOidcLink(
                projectId, saved.getId(), GoogleOidc.PROVIDER, verified.subject(), email));
        eventPublisher.publishEvent(AuditEvent.byProjectUser(
                projectId, "project_user.google_provisioned", "project_user",
                Objects.toString(saved.getId(), null), saved.getId(),
                Map.of("email", saved.getEmail(), "subject", verified.subject())));
        sessionAuthenticator.establishFederatedSession(
                projectId, saved.getId(), GoogleOidc.FACTOR, Instant.now(), request, response);
        return new GoogleLoginOutcome.LoggedIn(continueUrl);
    }

    private ProjectOidcIdp requireEnabledConfig(UUID projectId) {
        ProjectOidcIdp config = idpRepository.findByProjectId(projectId).orElse(null);
        if (config == null) {
            throw new OidcFederationException("not_configured", "Google federation is not configured for this project.");
        }
        if (!config.isEnabled()) {
            throw new OidcFederationException("disabled", "Google federation is disabled for this project.");
        }
        return config;
    }

    private String redirectUri(ProjectAuthenticationContext ctx, HttpServletRequest request) {
        StringBuilder b = new StringBuilder()
                .append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        if (port > 0 && !(("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443))) {
            b.append(':').append(port);
        }
        return b.append("/api/p/").append(ctx.projectSlug()).append("/login/google/callback").toString();
    }

    private void recordFailedLoginBestEffort(ProjectUser user, Instant now) {
        try {
            user.recordFailedLogin(now, loginProperties.maxAttempts(), loginProperties.lockoutDuration());
            userRepository.save(user);
        } catch (RuntimeException exception) {
            log.warn("Failed to record Google-link failed login: userId={}", user.getId(), exception);
        }
    }

    private String chooseDisplayName(VerifiedGoogleIdToken verified, String email) {
        String name = StringUtils.hasText(verified.name()) ? verified.name()
                : StringUtils.hasText(verified.givenName()) ? verified.givenName() : email;
        return name.length() <= DISPLAY_NAME_MAX ? name : name.substring(0, DISPLAY_NAME_MAX);
    }

    private String randomSecret() {
        byte[] buffer = new byte[48];
        random.nextBytes(buffer);
        return urlEncoder.encodeToString(buffer);
    }

    /** Builds the Google authorization URL from the configured endpoints and the ticket. */
    private record GoogleAuthorizationUrl(
            OidcFederationProperties.Google google, ProjectOidcIdp config,
            OidcLoginState ticket, String redirectUri
    ) {
        String toUrl() {
            return google.authorizationEndpoint()
                    + "?client_id=" + encode(config.getClientId())
                    + "&response_type=code"
                    + "&scope=" + encode(google.scope())
                    + "&redirect_uri=" + encode(redirectUri)
                    + "&state=" + encode(ticket.state())
                    + "&nonce=" + encode(ticket.nonce());
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
