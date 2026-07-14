package dev.unzor.nexus.sdk.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regresión del fix P1 a nivel de <b>cadena de seguridad</b>: el endpoint de
 * back-channel logout debe ser {@code permitAll} y estar excluido de CSRF en la
 * cadena OIDC del cliente, porque Nexus le hace POST <i>sin</i> sesión ni token
 * CSRF. Antes del fix recibía 403 (CSRF) o 302 (redirect a login) y nunca llegaba
 * al controlador.
 *
 * <p>Se carga la cadena REAL del starter ({@code NexusSecurityAutoConfiguration}):
 * se provee un {@link ClientRegistrationRepository} de mentira para que el bean
 * de discovery haga back-off y no haya red. Un POST sin {@code Authorization} ni
 * token CSRF, con {@code logout_token} ausente, debe alcanzar el controlador →
 * 400 (parámetro requerido). Si la cadena bloqueara sería 401/403/302.</p>
 */
@SpringBootTest(
        classes = NexusClientFilterChainTest.App.class,
        properties = {
                "nexus.security.issuer=https://issuer.example.com/p/demo",
                "nexus.security.backchannel-logout-path=/logout/backchannel"
        })
@AutoConfigureMockMvc
class NexusClientFilterChainTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void backchannelEndpointReachableWithoutAuthOrCsrf() throws Exception {
        mvc.perform(post("/logout/backchannel"))
                .andExpect(status().isBadRequest()); // 400 del controlador = la cadena NO bloqueó
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(nexusRegistration());
        }

        private static ClientRegistration nexusRegistration() {
            return ClientRegistration.withRegistrationId("nexus")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile")
                    .issuerUri("https://issuer.example.com/p/demo")
                    .authorizationUri("https://issuer.example.com/p/demo/oauth2/authorize")
                    .tokenUri("https://issuer.example.com/p/demo/oauth2/token")
                    .jwkSetUri("https://issuer.example.com/p/demo/oauth2/jwks")
                    .userInfoUri("https://issuer.example.com/p/demo/userinfo")
                    .userNameAttributeName("sub")
                    .clientName("Nexus")
                    .build();
        }
    }
}
