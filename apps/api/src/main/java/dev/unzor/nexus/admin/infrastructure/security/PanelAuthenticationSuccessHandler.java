package dev.unzor.nexus.admin.infrastructure.security;

import dev.unzor.nexus.admin.application.service.RecordLoginService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;

public class PanelAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final PanelContinueUrlValidator continueUrlValidator;
    private final RecordLoginService recordLoginService;
    private final PanelSessionInitializer sessionInitializer;

    public PanelAuthenticationSuccessHandler(
            PanelContinueUrlValidator continueUrlValidator,
            RecordLoginService recordLoginService,
            PanelSessionInitializer sessionInitializer
    ) {
        this.continueUrlValidator = continueUrlValidator;
        this.recordLoginService = recordLoginService;
        this.sessionInitializer = sessionInitializer;
        setDefaultTargetUrl(continueUrlValidator.defaultDashboardUrl());
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        sessionInitializer.initialize(request, authentication);
        recordLogin(authentication);

        String continueUrl = request.getParameter("continue");
        if (continueUrlValidator.isAllowed(continueUrl)) {
            getRedirectStrategy().sendRedirect(request, response, continueUrl);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private void recordLogin(Authentication authentication) {
        if (authentication.getPrincipal() instanceof NexusAccountPrincipal principal) {
            recordLoginService.recordLogin(principal.accountId());
        }
    }
}
