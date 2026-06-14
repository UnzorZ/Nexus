package dev.unzor.nexus.admin.api.controller;

import dev.unzor.nexus.admin.api.dto.NexusAccountDetails;
import dev.unzor.nexus.admin.application.service.GetNexusAccountService;
import dev.unzor.nexus.admin.infrastructure.security.NexusAccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/panel/v1")
class PanelSessionController {

    private final GetNexusAccountService getNexusAccountService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    PanelSessionController(GetNexusAccountService getNexusAccountService) {
        this.getNexusAccountService = getNexusAccountService;
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
}
