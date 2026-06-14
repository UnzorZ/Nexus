package dev.unzor.nexus.admin.api.controller;

import dev.unzor.nexus.admin.api.dto.NexusAccountDetails;
import dev.unzor.nexus.admin.api.dto.SessionSummary;
import dev.unzor.nexus.admin.application.configuration.PanelSessionConfiguration;
import dev.unzor.nexus.admin.application.service.GetNexusAccountService;
import dev.unzor.nexus.admin.application.service.PanelSessionService;
import dev.unzor.nexus.admin.infrastructure.security.NexusAccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/panel/v1")
class PanelSessionController {

    private final GetNexusAccountService getNexusAccountService;
    private final PanelSessionService panelSessionService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    PanelSessionController(
            GetNexusAccountService getNexusAccountService,
            PanelSessionService panelSessionService
    ) {
        this.getNexusAccountService = getNexusAccountService;
        this.panelSessionService = panelSessionService;
        logoutHandler.setClearAuthentication(true);
        logoutHandler.setInvalidateHttpSession(true);
    }

    @GetMapping("/csrf")
    @ResponseStatus(NO_CONTENT)
    void csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
    }

    @GetMapping("/me")
    NexusAccountDetails currentAccount(@AuthenticationPrincipal NexusAccountPrincipal principal) {
        return getNexusAccountService.getById(principal.accountId());
    }

    @PostMapping("/session/logout")
    @ResponseStatus(NO_CONTENT)
    void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        logoutHandler.logout(request, response, authentication);
    }

    /**
     * Lista las sesiones del panel de la cuenta autenticada, con la sesión actual
     * primero y el resto por último acceso descendente.
     */
    @GetMapping("/sessions")
    List<SessionSummary> listSessions(
            @AuthenticationPrincipal NexusAccountPrincipal principal,
            HttpSession session
    ) {
        return panelSessionService.listForAccount(
                principal.accountId(),
                currentSessionPublicId(session)
        );
    }

    /**
     * Revoca una sesión concreta por su identificador público. Si se revoca la sesión
     * actual, también limpia la autenticación y la cookie.
     */
    @DeleteMapping("/sessions/{publicSessionId}")
    @ResponseStatus(NO_CONTENT)
    void revokeSession(
            @PathVariable UUID publicSessionId,
            @AuthenticationPrincipal NexusAccountPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        panelSessionService.revokeByPublicId(principal.accountId(), publicSessionId);

        if (isCurrentSession(request.getSession(false), publicSessionId)) {
            logoutHandler.logout(request, response, authentication);
        }
    }

    /**
     * Revoca todas las sesiones de la cuenta, incluida la actual, y limpia autenticación
     * y cookie.
     */
    @DeleteMapping("/sessions")
    @ResponseStatus(NO_CONTENT)
    void revokeAllSessions(
            @AuthenticationPrincipal NexusAccountPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        panelSessionService.revokeAllForAccount(principal.accountId());
        logoutHandler.logout(request, response, authentication);
    }

    private static UUID currentSessionPublicId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PanelSessionConfiguration.SESSION_PUBLIC_ID);
        return value instanceof String text ? UUID.fromString(text) : null;
    }

    private static boolean isCurrentSession(HttpSession session, UUID publicSessionId) {
        UUID current = currentSessionPublicId(session);
        return current != null && current.equals(publicSessionId);
    }
}
