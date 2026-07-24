package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.identity.application.service.ProjectAllowedOriginsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectOauthAuthenticationEntryPointTest {

    @Mock
    private ProjectAllowedOriginsResolver resolver;

    private ProjectOauthAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        entryPoint = new ProjectOauthAuthenticationEntryPoint("https://nexus.unzor.dev", resolver);
    }

    @Test
    void redirectsToAppOriginWhenRedirectUriIsRegistered() throws Exception {
        when(resolver.originsForSlug("flip")).thenReturn(Set.of("https://flip.unzor.dev"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/p/flip/oauth2/authorize");
        req.addParameter("redirect_uri", "https://flip.unzor.dev/login/oauth2/code/nexus");
        req.setScheme("https");
        req.setServerName("nexus-api.unzor.dev");
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new AuthenticationCredentialsNotFoundException("no session"));

        assertThat(res.getRedirectedUrl()).startsWith("https://flip.unzor.dev/p/flip/login?continue=");
    }

    @Test
    void fallsBackToFrontendBaseUrlWhenRedirectUriNotRegistered() throws Exception {
        when(resolver.originsForSlug("flip")).thenReturn(Set.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/p/flip/oauth2/authorize");
        req.addParameter("redirect_uri", "https://evil.example/cb");
        req.setScheme("https");
        req.setServerName("nexus-api.unzor.dev");
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new AuthenticationCredentialsNotFoundException("no session"));

        assertThat(res.getRedirectedUrl()).startsWith("https://nexus.unzor.dev/p/flip/login?continue=");
    }

    @Test
    void globalRouteRedirectsToAuthenticationRequired() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/oauth2/authorize");
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new AuthenticationCredentialsNotFoundException("no session"));

        assertThat(res.getRedirectedUrl()).isEqualTo("https://nexus.unzor.dev/oauth2/authentication-required");
    }
}
