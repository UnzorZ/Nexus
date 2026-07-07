package dev.unzor.nexus.admin.application.configuration;

import dev.unzor.nexus.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code NEXUS_SESSION_TIMEOUT} controls the real
 * {@code RedisIndexedSessionRepository} inactivity interval and that
 * {@code nexus.session.cookie.*} properties are applied to the Spring Session cookie.
 *
 * <p>Uses non-default values to ensure they take effect (a default-only test could pass
 * even if configuration were ignored).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "nexus.session.timeout=2m",
        "nexus.session.cookie.name=NEXUSID",
        "nexus.session.cookie.secure=true",
        "nexus.session.cookie.max-age=5m"
})
class PanelSessionConfigurationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sessionTimeoutMustBePositive() {
        PanelSessionConfiguration configuration = new PanelSessionConfiguration();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> configuration.sessionTimeoutCustomizer(Duration.ZERO))
                .withMessage("nexus.session.timeout must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> configuration.sessionTimeoutCustomizer(Duration.ofSeconds(-1)))
                .withMessage("nexus.session.timeout must be positive");
    }

    @Test
    void sessionTimeoutReflectsConfiguredValue() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String token = csrfCookie.getValue();

        // Register + login; the created Redis session must use the configured timeout.
        String email = "timeout-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/panel/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", token)
                        .cookie(csrfCookie)
                        .content("{\"email\":\"" + email + "\",\"password\":\"plain-password\",\"displayName\":\"Owner\"}"))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/panel/v1/session/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\""+email+"\",\"password\":\"plain-password\"}"))
                        .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = login.getResponse().getCookie("NEXUSID");
        assertThat(sessionCookie).as("NEXUSID cookie issued").isNotNull();

        // The session list reports the configured max-inactive interval.
        MvcResult sessions = mockMvc.perform(get("/api/panel/v1/sessions")
                        .accept(MediaType.APPLICATION_JSON)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(sessions.getResponse().getContentAsString())
                .contains("\"maxInactiveIntervalSeconds\":120");
    }

    @Test
    void cookieReflectsConfiguredNameAndSecureAndMaxAge() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        // The session cookie is issued on login; inspect Set-Cookie attributes.
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String token = csrfCookie.getValue();

        // Register + login to obtain the configured session cookie.
        String email = "cfg-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/panel/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", token)
                        .cookie(csrfCookie)
                        .content("{\"email\":\"" + email + "\",\"password\":\"plain-password\",\"displayName\":\"Owner\"}"))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/panel/v1/session/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\""+email+"\",\"password\":\"plain-password\"}"))
                        .andExpect(status().isOk())
                .andReturn();

        // The Set-Cookie header for the session must use the configured name, Secure and Max-Age.
        java.util.Collection<String> setCookies = login.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookies).isNotEmpty();
        String sessionCookie = setCookies.stream()
                .filter(c -> c.startsWith("NEXUSID="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("NEXUSID cookie not issued; got: " + setCookies));

        assertThat(sessionCookie).contains("Secure");
        // DefaultCookieSerializer emits Max-Age=300 for a 5m max-age.
        assertThat(sessionCookie).contains("Max-Age=300");
    }
}
