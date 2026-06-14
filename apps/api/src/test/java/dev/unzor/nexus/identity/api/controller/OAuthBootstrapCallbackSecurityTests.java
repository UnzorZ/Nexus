package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OAuthBootstrapCallbackSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bootstrapCallbackReturnsNoContent() throws Exception {
        mockMvc.perform(get("/oauth2/bootstrap/callback"))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownOAuthPathOutsideAuthorizationServerEndpointsIsDenied() throws Exception {
        mockMvc.perform(get("/oauth2/unknown-residual-path"))
                .andExpect(status().isForbidden());
    }
}
