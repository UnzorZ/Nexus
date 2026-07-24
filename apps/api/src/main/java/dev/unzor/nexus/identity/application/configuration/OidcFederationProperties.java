package dev.unzor.nexus.identity.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration of external OIDC federation (Google), bound to the prefix
 * {@code nexus.identity.federation}. Every value has a working default so a Nexus
 * instance runs without explicit federation configuration; a project still has to store
 * its own Google client credentials before login is available.
 */
@ConfigurationProperties("nexus.identity.federation")
public record OidcFederationProperties(
        Google google,
        @DefaultValue("5m") Duration stateTtl,
        @DefaultValue("10m") Duration linkTicketTtl
) {

    /**
     * Google endpoints and identifiers. Google issues {@code iss} as either of the two
     * configured values, so both are accepted during id_token verification.
     */
    public record Google(
            @DefaultValue("https://accounts.google.com") String issuer,
            @DefaultValue("accounts.google.com") String alternateIssuer,
            @DefaultValue("https://accounts.google.com/o/oauth2/v2/auth") String authorizationEndpoint,
            @DefaultValue("https://oauth2.googleapis.com/token") String tokenEndpoint,
            @DefaultValue("https://www.googleapis.com/oauth2/v3/certs") String jwksUri,
            @DefaultValue("openid email profile") String scope
    ) {
    }

    public OidcFederationProperties {
        if (google == null) {
            google = new Google(
                    "https://accounts.google.com",
                    "accounts.google.com",
                    "https://accounts.google.com/o/oauth2/v2/auth",
                    "https://oauth2.googleapis.com/token",
                    "https://www.googleapis.com/oauth2/v3/certs",
                    "openid email profile");
        }
    }
}
