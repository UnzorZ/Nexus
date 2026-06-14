package dev.unzor.nexus.identity.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.oauth.bootstrap")
public record NexusOAuthBootstrapProperties(
        String registeredClientId,
        String clientId,
        String clientSecret,
        String redirectUri,
        String postLogoutRedirectUri
) {
    public NexusOAuthBootstrapProperties {
        if (registeredClientId == null || registeredClientId.isBlank()) {
            registeredClientId = "nexus-oidc-client";
        }
        if (clientId == null || clientId.isBlank()) {
            clientId = "oidc-client";
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = "http://127.0.0.1:8080/oauth2/bootstrap/callback";
        }
    }
}
