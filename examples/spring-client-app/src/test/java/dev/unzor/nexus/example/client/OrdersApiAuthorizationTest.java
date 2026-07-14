package dev.unzor.nexus.example.client;

import dev.unzor.nexus.sdk.security.NexusPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authorization test for the {@code permissions}-claim enforcement.
 * Drives {@code GET /api/orders} with a mocked JWT carrying various
 * {@code permissions} values and asserts the glob-match (via the starter's
 * {@code @perm} bean, {@link NexusPermissionService}) yields 200 vs 403.
 *
 * <p>The OAuth2 client/resource-server Boot auto-configurations (and the starter's
 * own auto-configs — they need {@code nexus.*}) are inactive, so the slice test
 * doesn't fetch Nexus's discovery at startup. {@link TestSecurity} provides a
 * minimal resource-server chain + the {@code @perm} bean; the {@code jwt()}
 * post-processor short-circuits decoding and injects the authentication directly.</p>
 */
@WebMvcTest(OrdersApiController.class)
@Import(OrdersApiAuthorizationTest.TestSecurity.class)
@ImportAutoConfiguration(exclude = {OAuth2ClientAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
class OrdersApiAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void namespaceWildcardGrantsAccess() throws Exception {
        mvc.perform(get("/api/orders").with(jwt().jwt(j -> j.claim("permissions", List.of("orders.*")))))
                .andExpect(status().isOk());
    }

    @Test
    void globalWildcardGrantsAccess() throws Exception {
        mvc.perform(get("/api/orders").with(jwt().jwt(j -> j.claim("permissions", List.of("*")))))
                .andExpect(status().isOk());
    }

    @Test
    void exactPermissionGrantsAccess() throws Exception {
        mvc.perform(get("/api/orders").with(jwt().jwt(j -> j.claim("permissions", List.of("orders.read")))))
                .andExpect(status().isOk());
    }

    @Test
    void emptyPermissionsIsForbidden() throws Exception {
        mvc.perform(get("/api/orders").with(jwt().jwt(j -> j.claim("permissions", List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingPermissionsClaimIsForbidden() throws Exception {
        mvc.perform(get("/api/orders").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unrelatedPermissionIsForbidden() throws Exception {
        mvc.perform(get("/api/orders").with(jwt().jwt(j -> j.claim("permissions", List.of("users.read")))))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurity {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher("/api/**")
                    .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                    .csrf(org.springframework.security.config.annotation.web.configurers.CsrfConfigurer::disable)
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .build();
        }

        /** Stub decoder — never invoked: the {@code jwt()} post-processor short-circuits decoding. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> { throw new IllegalStateException("JwtDecoder must not be invoked in this test"); };
        }

        @Bean("perm")
        NexusPermissionService perm() {
            return new NexusPermissionService();
        }
    }
}
