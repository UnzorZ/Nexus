package dev.unzor.nexus.admin.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Conserva {@code continue} tras credenciales incorrectas cuando la URL es segura.
 */
public class PanelAuthenticationFailureHandler implements AuthenticationFailureHandler {

    static final String FAILURE_URL = "/panel/login?error=true";

    private final PanelContinueUrlValidator continueUrlValidator;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public PanelAuthenticationFailureHandler(PanelContinueUrlValidator continueUrlValidator) {
        this.continueUrlValidator = continueUrlValidator;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        redirectStrategy.sendRedirect(request, response, resolveFailureUrl(request));
    }

    String resolveFailureUrl(HttpServletRequest request) {
        String continueUrl = request.getParameter("continue");
        if (continueUrlValidator.isAllowed(continueUrl)) {
            return FAILURE_URL + "&continue=" + URLEncoder.encode(continueUrl, StandardCharsets.UTF_8);
        }
        return FAILURE_URL;
    }
}
