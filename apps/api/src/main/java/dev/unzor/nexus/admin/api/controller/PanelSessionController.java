package dev.unzor.nexus.admin.api.controller;

import dev.unzor.nexus.admin.api.dto.NexusAccountDetails;
import dev.unzor.nexus.admin.api.dto.SessionSummary;
import dev.unzor.nexus.admin.api.requests.LoginRequest;
import dev.unzor.nexus.admin.application.configuration.PanelSessionConfiguration;
import dev.unzor.nexus.admin.application.service.GetNexusAccountService;
import dev.unzor.nexus.admin.application.service.PanelSessionService;
import dev.unzor.nexus.admin.application.service.RecordLoginService;
import dev.unzor.nexus.admin.infrastructure.security.NexusAccountPrincipal;
import dev.unzor.nexus.admin.infrastructure.security.PanelSessionInitializer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final RecordLoginService recordLoginService;
    private final PanelSessionInitializer sessionInitializer;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    PanelSessionController(
            GetNexusAccountService getNexusAccountService,
            PanelSessionService panelSessionService,
            RecordLoginService recordLoginService,
            PanelSessionInitializer sessionInitializer,
            @Qualifier("panelAuthenticationManager") AuthenticationManager authenticationManager
    ) {
        this.getNexusAccountService = getNexusAccountService;
        this.panelSessionService = panelSessionService;
        this.recordLoginService = recordLoginService;
        this.sessionInitializer = sessionInitializer;
        this.authenticationManager = authenticationManager;
        logoutHandler.setClearAuthentication(true);
        logoutHandler.setInvalidateHttpSession(true);
    }

    /**
     * Inicio de sesión JSON para el panel Nexus. Recibe email y contraseña, los
     * autentica contra el {@link AuthenticationManager} del panel y, si es
     * correcto, establece el {@code SecurityContext} en la sesión HTTP de forma
     * que el resto de endpoints de la API del panel lo reconozcan.
     * <p>
     * La sesión y la cookie {@code JSESSIONID} las crea Spring Session; el
     * frontend Next.js debe enviar la cookie y la cabecera {@code X-XSRF-TOKEN}
     * previamente obtenida de {@code GET /api/panel/v1/csrf}.
     */
    @PostMapping("/session/login")
    @ResponseStatus(HttpStatus.OK)
    NexusAccountDetails login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(request.email(), request.password());

        Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(token);
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        // Persistir SecurityContext en la sesión HTTP
        var securityContext = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authenticated);
        securityContextRepository.saveContext(securityContext, servletRequest, servletResponse);

        sessionInitializer.initialize(servletRequest, authenticated);

        NexusAccountPrincipal principal = (NexusAccountPrincipal) authenticated.getPrincipal();
        recordLoginService.recordLogin(principal.accountId());

        return getNexusAccountService.getById(principal.accountId());
    }

    /**
     * Expone el token CSRF del panel junto con la cookie {@code XSRF-TOKEN}.
     *
     * <p>Devolver el token en el cuerpo (no solo en la cookie) es lo que permite
     * que un frontend cross-origin —que no puede leer {@code document.cookie}
     * porque la cookie la emite el host del API— lo coloque en la cabecera
     * {@code X-XSRF-TOKEN}. La cookie sigue emitiéndose (vía {@code getToken()},
     * que fuerza el guardado en el repositorio) para el double-submit: el
     * navegador la envía de vuelta en las escrituras con credenciales.</p>
     */
    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return csrfToken;
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
