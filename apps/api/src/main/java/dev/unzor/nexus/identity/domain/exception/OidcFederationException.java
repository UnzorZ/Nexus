package dev.unzor.nexus.identity.domain.exception;

/**
 * Runtime failure of an OIDC federation operation (callback handling, id_token
 * verification, code exchange, state/nonce validation). Carries a stable machine-readable
 * {@code code} so the API layer can map it to a redirect or a problem-detail without
 * leaking the internal detail message.
 */
public class OidcFederationException extends RuntimeException {

    private final String code;

    public OidcFederationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public OidcFederationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
