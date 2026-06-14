package dev.unzor.nexus.admin.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class PanelAuthenticationFailureHandlerTests {

    private final PanelContinueUrlValidator validator =
            new PanelContinueUrlValidator("http://localhost:3000");
    private final PanelAuthenticationFailureHandler handler =
            new PanelAuthenticationFailureHandler(validator);

    @Test
    void failureUrlIncludesSafeContinueWhenAllowed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("continue", "http://localhost:3000/dashboard");

        assertThat(handler.resolveFailureUrl(request))
                .isEqualTo("/panel/login?error=true&continue=http%3A%2F%2Flocalhost%3A3000%2Fdashboard");
    }

    @Test
    void failureUrlOmitsUnsafeContinue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("continue", "https://evil.example/phish");

        assertThat(handler.resolveFailureUrl(request))
                .isEqualTo(PanelAuthenticationFailureHandler.FAILURE_URL);
    }

    @Test
    void concurrentFailuresResolveIndependentUrlsWithoutSharedState() throws Exception {
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < iterations; index++) {
                final boolean withSafeContinue = index % 2 == 0;
                futures.add(executor.submit(() -> {
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    if (withSafeContinue) {
                        request.setParameter("continue", "http://localhost:3000/dashboard");
                    }
                    return handler.resolveFailureUrl(request);
                }));
            }

            for (int index = 0; index < iterations; index++) {
                String failureUrl = futures.get(index).get();
                if (index % 2 == 0) {
                    assertThat(failureUrl).contains("continue=");
                }
                else {
                    assertThat(failureUrl).isEqualTo(PanelAuthenticationFailureHandler.FAILURE_URL);
                }
            }
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void onAuthenticationFailureRedirectsToResolvedUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/panel/login");
        request.setParameter("continue", "http://localhost:3000/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/panel/login?error=true&continue=http%3A%2F%2Flocalhost%3A3000%2Fsettings");
    }
}
