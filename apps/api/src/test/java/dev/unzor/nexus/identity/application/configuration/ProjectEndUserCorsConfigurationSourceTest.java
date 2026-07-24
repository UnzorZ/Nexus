package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.identity.application.service.ProjectAllowedOriginsResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectEndUserCorsConfigurationSourceTest {

    @Mock
    private ProjectAllowedOriginsResolver resolver;

    @Test
    void returnsNullWhenNoOriginHeader() {
        ProjectEndUserCorsConfigurationSource source =
                new ProjectEndUserCorsConfigurationSource(resolver, Set.of("https://nexus.unzor.dev"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/p/flip/csrf");

        assertThat(source.getCorsConfiguration(req)).isNull();
    }

    @Test
    void allowsRegisteredOriginEchoingBaselineAndProjectOrigins() {
        when(resolver.originsForSlug("flip")).thenReturn(Set.of("https://flip.unzor.dev"));
        ProjectEndUserCorsConfigurationSource source =
                new ProjectEndUserCorsConfigurationSource(resolver, Set.of("https://nexus.unzor.dev"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/p/flip/login");
        req.addHeader("Origin", "https://flip.unzor.dev");

        CorsConfiguration config = source.getCorsConfiguration(req);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins())
                .containsExactlyInAnyOrder("https://nexus.unzor.dev", "https://flip.unzor.dev");
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getAllowedMethods()).contains("POST", "GET", "OPTIONS");
    }

    @Test
    void configExcludesUnregisteredOriginSoProcessorRejectsIt() {
        when(resolver.originsForSlug("flip")).thenReturn(Set.of());
        ProjectEndUserCorsConfigurationSource source =
                new ProjectEndUserCorsConfigurationSource(resolver, Set.of("https://nexus.unzor.dev"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/p/flip/login");
        req.addHeader("Origin", "https://evil.example");

        CorsConfiguration config = source.getCorsConfiguration(req);

        // Non-null config whose allowedOrigins excludes the request origin: DefaultCorsProcessor
        // .checkOrigin returns null -> 403 "Invalid CORS request" (not a silent pass-through).
        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).doesNotContain("https://evil.example");
    }
}
