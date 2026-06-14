package dev.unzor.nexus.admin.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;

public class PanelAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final PanelContinueUrlValidator continueUrlValidator;

    public PanelAuthenticationSuccessHandler(PanelContinueUrlValidator continueUrlValidator) {
        this.continueUrlValidator = continueUrlValidator;
        setDefaultTargetUrl(continueUrlValidator.defaultDashboardUrl());
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        String continueUrl = request.getParameter("continue");
        if (continueUrlValidator.isAllowed(continueUrl)) {
            getRedirectStrategy().sendRedirect(request, response, continueUrl);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
