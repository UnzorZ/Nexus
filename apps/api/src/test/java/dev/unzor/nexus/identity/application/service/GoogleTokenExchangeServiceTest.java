package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.OidcFederationProperties;
import dev.unzor.nexus.identity.domain.exception.OidcFederationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class GoogleTokenExchangeServiceTest {

    private GoogleTokenExchangeService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new GoogleTokenExchangeService(builder.build(),
                new OidcFederationProperties(null, Duration.ofMinutes(5), Duration.ofMinutes(10)));
    }

    @Test
    void exchangePostsFormAndParsesIdToken() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess(
                        "{\"id_token\":\"id-token-value\",\"access_token\":\"access\",\"token_type\":\"Bearer\",\"scope\":\"openid\"}",
                        MediaType.APPLICATION_JSON));

        GoogleTokenSet tokens = service.exchange("the-code", "https://app/cb", "client-id", "secret");

        server.verify();
        assertThat(tokens.idToken()).isEqualTo("id-token-value");
        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void exchangeFailsWhenResponseHasNoIdToken() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"access\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.exchange("code", "https://app/cb", "client-id", "secret"))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("exchange_failed");
    }

    @Test
    void exchangeFailsOnGoogleError() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> service.exchange("code", "https://app/cb", "client-id", "secret"))
                .isInstanceOf(OidcFederationException.class)
                .extracting("code").isEqualTo("exchange_failed");
    }
}
