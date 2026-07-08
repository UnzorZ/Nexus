package dev.unzor.nexus.shared.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios del {@link RateLimitFilter} (se invoca directamente, sin contexto
 * Spring). El smoke end-to-end real se hace contra el servidor arrancado.
 */
class RateLimitFilterTest {

    private static RateLimitProperties props(boolean enabled, boolean trustXff) {
        // Capacidad pequeña (2) y reposición de 1s para que el test se agote rápido
        // sin depender del paso del tiempo real.
        return new RateLimitProperties(
                enabled, trustXff, Duration.ofMinutes(5),
                2, 2, Duration.ofSeconds(1),
                2, 2, Duration.ofSeconds(1));
    }

    private static MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI(uri);
        request.setRemoteAddr("1.2.3.4");
        return request;
    }

    private static int[] countingChain() {
        return new int[]{0};
    }

    @Test
    void authEndpointBlocksAfterCapacity() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(props(true, false), new RateLimitBucketStore(props(true, false)));

        for (int i = 0; i < 2; i++) {
            int[] passed = countingChain();
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(post("/api/panel/v1/session/login"), res, (q, r) -> passed[0]++);
            assertThat(passed[0]).as("attempt %d should pass", i + 1).isEqualTo(1);
            assertThat(res.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        int[] passed = countingChain();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(post("/api/panel/v1/session/login"), res, (q, r) -> passed[0]++);
        assertThat(passed[0]).as("attempt over capacity must not reach the chain").isZero();
        assertThat(res.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(res.getHeader("Retry-After")).isNotNull();
        assertThat(res.getHeader("Retry-After").toString()).matches("\\d+");
        assertThat(res.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void nonAuthPathIsNeverLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(props(true, false), new RateLimitBucketStore(props(true, false)));
        MockHttpServletRequest req = post("/api/panel/v1/projects");
        req.setMethod("GET");

        for (int i = 0; i < 20; i++) {
            int[] passed = countingChain();
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, (q, r) -> passed[0]++);
            assertThat(passed[0]).as("attempt %d should pass", i + 1).isEqualTo(1);
            assertThat(res.getStatus()).isEqualTo(HttpStatus.OK.value());
        }
    }

    @Test
    void generalTierHasIndependentBucket() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(props(true, false), new RateLimitBucketStore(props(true, false)));

        // Agota el bucket AUTH (login) del IP.
        for (int i = 0; i < 2; i++) {
            filter.doFilter(post("/api/panel/v1/session/login"), new MockHttpServletResponse(), (q, r) -> {
            });
        }

        // El tier GENERAL (registro) usa otro bucket: 2 intentos pasan pese a que
        // el AUTH está agotado (mismo IP).
        for (int i = 0; i < 2; i++) {
            int[] passed = countingChain();
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(post("/api/p/foo/register"), res, (q, r) -> passed[0]++);
            assertThat(passed[0]).as("general attempt %d should pass", i + 1).isEqualTo(1);
        }

        // 3.er registro bloqueado.
        int[] passed = countingChain();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(post("/api/p/foo/register"), res, (q, r) -> passed[0]++);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void trustForwardedForKeysByFirstXffIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(props(true, true), new RateLimitBucketStore(props(true, true)));

        MockHttpServletRequest ipA = post("/api/panel/v1/session/login");
        ipA.addHeader("X-Forwarded-For", "9.9.9.9");
        MockHttpServletRequest ipB = post("/api/panel/v1/session/login");
        ipB.addHeader("X-Forwarded-For", "8.8.8.8");

        // Agota el bucket de 9.9.9.9.
        for (int i = 0; i < 2; i++) {
            filter.doFilter(ipA, new MockHttpServletResponse(), (q, r) -> {
            });
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(ipA, blocked, (q, r) -> {
        });
        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        // 8.8.8.8 tiene su propio bucket (mismo remoteAddr, distinto XFF) → pasa.
        int[] passed = countingChain();
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(ipB, ok, (q, r) -> passed[0]++);
        assertThat(passed[0]).isEqualTo(1);
        assertThat(ok.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void disabledFilterPassesEverything() throws Exception {
        RateLimitProperties disabled = props(false, false);
        RateLimitFilter filter = new RateLimitFilter(disabled, new RateLimitBucketStore(disabled));

        for (int i = 0; i < 30; i++) {
            int[] passed = countingChain();
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(post("/api/panel/v1/session/login"), res, (q, r) -> passed[0]++);
            assertThat(passed[0]).isEqualTo(1);
        }
    }
}
