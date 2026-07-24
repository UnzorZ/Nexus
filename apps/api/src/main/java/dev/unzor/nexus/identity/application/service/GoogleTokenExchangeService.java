package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.OidcFederationProperties;
import dev.unzor.nexus.identity.domain.exception.OidcFederationException;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Exchanges a Google authorization code for a token set. Sends a form-urlencoded POST to
 * Google's token endpoint with the per-project client id and the decrypted client secret.
 * The HTTP client is injected so unit tests can drive it through a {@code MockRestServiceServer}.
 */
public class GoogleTokenExchangeService {

    private final RestClient httpClient;
    private final OidcFederationProperties properties;

    public GoogleTokenExchangeService(RestClient httpClient, OidcFederationProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    /**
     * @param code         the authorization code returned by Google
     * @param redirectUri  the same redirect URI used to build the authorization request
     * @param clientId     the per-project Google client id
     * @param clientSecret the per-project Google client secret (already decrypted)
     */
    public GoogleTokenSet exchange(String code, String redirectUri, String clientId, String clientSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        try {
            JsonNode body = httpClient.post()
                    .uri(properties.google().tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.path("id_token").isMissingNode()) {
                throw new OidcFederationException("exchange_failed", "Google token response did not contain an id_token.");
            }
            return new GoogleTokenSet(
                    body.path("id_token").asText(),
                    asTextOrNull(body, "access_token"),
                    body.path("token_type").asText("Bearer"),
                    asTextOrNull(body, "scope"));
        } catch (RestClientException exception) {
            throw new OidcFederationException("exchange_failed", "Failed to exchange the code with Google.", exception);
        }
    }

    private static String asTextOrNull(JsonNode node, String field) {
        return node.path(field).isMissingNode() ? null : node.path(field).asText();
    }
}
