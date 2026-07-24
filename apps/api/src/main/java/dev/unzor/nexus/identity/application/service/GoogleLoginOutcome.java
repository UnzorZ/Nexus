package dev.unzor.nexus.identity.application.service;

/**
 * Result of a Google federated callback or link completion. The controller switches on the
 * variant to pick the HTTP response (resume the authorization flow, prompt for the
 * re-authentication password, or redirect to an error page).
 */
public sealed interface GoogleLoginOutcome
        permits GoogleLoginOutcome.LoggedIn, GoogleLoginOutcome.LinkRequired, GoogleLoginOutcome.FederationError {

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

    /** The federated login failed; {@code code} is a stable, non-sensitive reason. */
    record FederationError(String code) implements GoogleLoginOutcome {
    }
}
