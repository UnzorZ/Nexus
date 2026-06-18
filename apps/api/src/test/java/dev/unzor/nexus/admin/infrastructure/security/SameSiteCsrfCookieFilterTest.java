package dev.unzor.nexus.admin.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que {@link SameSiteCsrfCookieFilter} añade {@code SameSite} y
 * {@code Secure} a la cookie CSRF cuando el repositorio la escribe vía
 * {@code addCookie} (camino Servlet 6) o vía cabecera {@code Set-Cookie}.
 *
 * <p>El filtro solo envuelve la respuesta mientras se procesa la cadena; por eso
 * las cookies se añaden sobre el wrapper capturado por la cadena, simulando al
 * {@code CsrfFilter} que escribe aguas abajo.</p>
 */
class SameSiteCsrfCookieFilterTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";

    @Test
    void rewritesCsrfCookieFromAddCookieWithSameSiteAndSecure() throws Exception {
        MockHttpServletResponse original = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new SameSiteCsrfCookieFilter(CSRF_COOKIE, "None", true)
                .doFilter(new MockHttpServletRequest(), original, chain);

        HttpServletResponse wrapper = (HttpServletResponse) chain.getResponse();
        wrapper.addCookie(new Cookie(CSRF_COOKIE, "token-123"));

        assertThat(original.getHeaders("Set-Cookie"))
                .singleElement()
                .asString()
                .contains("XSRF-TOKEN=token-123")
                .contains("Path=/")
                .contains("SameSite=None")
                .contains("Secure");
    }

    @Test
    void rewritesSetCookieHeaderWhenAlreadyPresent() throws Exception {
        MockHttpServletResponse original = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new SameSiteCsrfCookieFilter(CSRF_COOKIE, "None", true)
                .doFilter(new MockHttpServletRequest(), original, chain);

        HttpServletResponse wrapper = (HttpServletResponse) chain.getResponse();
        wrapper.addHeader("Set-Cookie", CSRF_COOKIE + "=abc; Path=/");

        assertThat(original.getHeaders("Set-Cookie").getFirst())
                .contains("SameSite=None")
                .contains("Secure");
    }

    @Test
    void doesNotTouchUnrelatedCookies() throws Exception {
        MockHttpServletResponse original = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new SameSiteCsrfCookieFilter(CSRF_COOKIE, "None", true)
                .doFilter(new MockHttpServletRequest(), original, chain);

        HttpServletResponse wrapper = (HttpServletResponse) chain.getResponse();
        wrapper.addCookie(new Cookie("OTHER", "value"));

        // La cookie ajena se propaga sin reescribir: nunca se le inyectan
        // SameSite/Secure (que solo aplican a la cookie CSRF).
        assertThat(original.getCookie("OTHER")).isNotNull();
        original.getHeaders("Set-Cookie").forEach(header ->
                assertThat(header).doesNotContain("SameSite").doesNotContain("Secure"));
    }

    @Test
    void omitsSecureAndKeepsLaxForLocalhost() throws Exception {
        MockHttpServletResponse original = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new SameSiteCsrfCookieFilter(CSRF_COOKIE, "Lax", false)
                .doFilter(new MockHttpServletRequest(), original, chain);

        HttpServletResponse wrapper = (HttpServletResponse) chain.getResponse();
        wrapper.addCookie(new Cookie(CSRF_COOKIE, "t"));

        String header = original.getHeaders("Set-Cookie").getFirst();
        assertThat(header).contains("SameSite=Lax").doesNotContain("Secure");
    }
}
