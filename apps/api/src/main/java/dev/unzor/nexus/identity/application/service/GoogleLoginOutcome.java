package dev.unzor.nexus.identity.application.service;

/**
 * Result of a Google federated callback or link completion. The controller switches on the
 * variant to pick the HTTP response (resume the authorization flow, prompt for the
 * re-authentication password, or redirect to an error page).
 */
public sealed interface GoogleLoginOutcome
        permits GoogleLoginOutcome.LoggedIn, GoogleLoginOutcome.LinkRequired,
                GoogleLoginOutcome.MfaRequired, GoogleLoginOutcome.FederationError {

    /** The user is authenticated; the original {@code continueUrl} may resume the flow. */
    record LoggedIn(String continueUrl) implements GoogleLoginOutcome {
    }

    /**
     * A verified Google identity is not yet linked, and its email matches an existing
     * account. The user must re-authenticate (password) before a link is created. The
     * matching account is never logged in by this outcome alone.
     */
    record LinkRequired(String email, String displayName) implements GoogleLoginOutcome {
    }

    /**
     * The linked account has TOTP MFA enabled: the federated first factor alone does not
     * authenticate. A pending MFA ticket is already parked in the (anonymous) session; the
     * user must complete the second factor via {@code POST /api/p/{slug}/login/mfa} — the
     * same endpoint the password flow uses. {@code continueUrl} is the original post-login
     * target, preserved for the resume after the code verifies.
     */
    record MfaRequired(String continueUrl) implements GoogleLoginOutcome {
    }

    /** The federated login failed; {@code code} is a stable, non-sensitive reason. */
    record FederationError(String code) implements GoogleLoginOutcome {
    }
}
