package dev.unzor.nexus.identity.application.service;

/**
 * Shared constants of Google OIDC federation: the provider key, the authentication factor
 * name recorded on the session, the session-attribute keys for the pending tickets, and
 * the entropy sizes of the state and nonce.
 */
public final class GoogleOidc {

    private GoogleOidc() {
    }

    /** Provider key persisted on federation links and used in queries ({@code "google"}). */
    public static final String PROVIDER = "google";

    /** Primary authentication-factor name for a Google login ({@code amr}/auth_time). */
    public static final String FACTOR = "google";

    /** Session attribute holding the single-use OIDC login state (state + nonce). */
    public static final String LOGIN_STATE_SESSION_KEY = "nexus.oidc.loginState";

    /** Session attribute holding a pending account-linking ticket during re-authentication. */
    public static final String LINK_TICKET_SESSION_KEY = "nexus.oidc.linkTicket";

    static final int STATE_BYTES = 32;
    static final int NONCE_BYTES = 32;
}
