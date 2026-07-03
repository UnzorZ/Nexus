package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.TestcontainersConfiguration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el comportamiento multi-issuer de B2: cada path {@code /p/{slug}}
 * resuelve su propio issuer ({@code {host}/p/{slug}}) y expone discovery/JWKS
 * prefijados por proyecto. No requiere un proyecto real en BD: el issuer se
 * deriva del path on the fly (SAS {@code multipleIssuersAllowed}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MultiIssuerEndpointsIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void discoveryExposesProjectScopedIssuerAndEndpoints() throws Exception {
        mockMvc.perform(get("/p/shop/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer", Matchers.endsWith("/p/shop")))
                .andExpect(jsonPath("$.authorization_endpoint", Matchers.containsString("/p/shop/oauth2/authorize")))
                .andExpect(jsonPath("$.jwks_uri", Matchers.containsString("/p/shop/oauth2/jwks")))
                .andExpect(jsonPath("$.token_endpoint", Matchers.containsString("/p/shop/oauth2/token")));
    }

    @Test
    void jwksEndpointServesTheSharedSigningKey() throws Exception {
        mockMvc.perform(get("/p/shop/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid", Matchers.startsWith("nexus-")));
    }

    @Test
    void differentSlugsResolveDifferentIssuers() throws Exception {
        mockMvc.perform(get("/p/warehouse/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer", Matchers.endsWith("/p/warehouse")));
    }
}
