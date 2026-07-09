package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.api.dto.ProjectUserDetails;
import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.domain.exception.EmailNotVerifiedException;
import dev.unzor.nexus.identity.domain.exception.MfaRequiredException;
import dev.unzor.nexus.identity.infrastructure.security.ProjectSessionAuthenticator;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;
import java.util.Map;

/**
 * API JSON de autenticación del usuario final ({@code /api/p/{slug}/**}). El login
 * delega en {@link ProjectSessionAuthenticator} (sin reimplementarlo): él rota la
 * sesión, indexa por {@code PROJECT_USER_ID}, añade el {@code FactorGrantedAuthority}
 * PASSWORD (auth_time) y persiste el {@code SecurityContext} — lo que el Authorization
 * Server reconoce al reanudar {@code /oauth2/authorize}. Reemplaza al login Thymeleaf.
 */
@RestController
@RequestMapping("/api/p/{projectSlug}")
class ProjectEndUserAuthController {

    private final ProjectSlugResolver slugResolver;
    private final ProjectSessionAuthenticator sessionAuthenticator;
    private final ProjectUserRepository userRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    ProjectEndUserAuthController(
            ProjectSlugResolver slugResolver,
            ProjectSessionAuthenticator sessionAuthenticator,
            ProjectUserRepository userRepository,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.slugResolver = slugResolver;
        this.sessionAuthenticator = sessionAuthenticator;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    record LoginRequest(String email, String password, String continueUrl) {
    }

    record LoginMfaRequest(String code, String continueUrl) {
    }

    @PostMapping("/login")
    ResponseEntity<Map<String, String>> login(
            @PathVariable String projectSlug,
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        try {
            sessionAuthenticator.authenticate(
                    context.projectId(), request.email(), request.password(), servletRequest, servletResponse);
        } catch (EmailNotVerifiedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "email_not_verified"));
        } catch (MfaRequiredException e) {
            // 200 (no-error) para que el SPA cambie al paso TOTP. La sesión lleva ya el
            // ticket MFA pendiente (anónima para SAS). El frontend reenvía continue.
            return ResponseEntity.ok(Map.of("code", "mfa_required"));
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "invalid_credentials"));
        }
        String redirect = authorizeResumeTarget(request.continueUrl(), context.projectSlug(), servletRequest);
        return ResponseEntity.ok(redirect == null ? Map.of() : Map.of("redirect", redirect));
    }

    /**
     * Completa el login MFA: verifica el código TOTP (o recovery) contra el ticket
     * pendiente fijado por {@code /login}. Si valida, establece la sesión con ambos
     * factores y devuelve el mismo shape {@code {redirect}} que consume el frontend.
     */
    @PostMapping("/login/mfa")
    ResponseEntity<Map<String, String>> loginMfa(
            @PathVariable String projectSlug,
            @RequestBody LoginMfaRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        try {
            sessionAuthenticator.completeMfaAuthentication(
                    context.projectId(), request.code(), servletRequest, servletResponse);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "invalid_code"));
        }
        String redirect = authorizeResumeTarget(request.continueUrl(), context.projectSlug(), servletRequest);
        return ResponseEntity.ok(redirect == null ? Map.of() : Map.of("redirect", redirect));
    }

    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken csrfToken) {
        csrfToken.getToken(); // fuerza la emisión de la cookie XSRF-TOKEN
        return csrfToken;     // y también la devuelve en el body (cross-origin: no se puede leer document.cookie)
    }

    @GetMapping("/me")
    ProjectUserDetails me(@PathVariable String projectSlug, @AuthenticationPrincipal ProjectUserPrincipal principal) {
        resolve(projectSlug); // 404 si el proyecto no existe
        return userRepository.findByProjectIdAndId(principal.projectId(), principal.userId())
                .map(ProjectUserDetails::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project user not found"));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @PathVariable String projectSlug,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        // Back-channel logout (OIDC RFC 8417): capturamos principal + issuer ANTES de que el
        // logoutHandler limpie el SecurityContext, y publicamos el evento para el fan-out
        // async a los clientes del realm con sesión para este usuario.
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal()
                instanceof dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal pup) {
            eventPublisher.publishEvent(
                    new dev.unzor.nexus.identity.application.service.BackChannelLogoutRequested(
                            auth.getName(), pup.projectId(), realmIssuer(servletRequest, projectSlug)));
        }
        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.setClearAuthentication(true);
        logoutHandler.setInvalidateHttpSession(true);
        logoutHandler.logout(servletRequest, servletResponse, null);
        return ResponseEntity.noContent().build();
    }

    /** Issuer del realm = {origin}/p/{slug}, derivado del request (consistente con el discovery). */
    private static String realmIssuer(HttpServletRequest request, String projectSlug) {
        StringBuilder b = new StringBuilder()
                .append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        if (port > 0 && !(("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443))) {
            b.append(':').append(port);
        }
        return b.append("/p/").append(projectSlug).toString();
    }

    private ProjectAuthenticationContext resolve(String projectSlug) {
        try {
            return slugResolver.resolve(projectSlug);
        } catch (ProjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }

    /**
     * Destino post-login válido: sólo la reanudación del authorize dentro del mismo
     * realm ({@code /p/{slug}/oauth2/...}), sea relativo (mismo origen) o absoluto en
     * el host del API. Guard anti open-redirect: cualquier otra URL → null (el frontend
     * va al portal por defecto).
     */
    private static String authorizeResumeTarget(String continueUrl, String projectSlug, HttpServletRequest request) {
        if (continueUrl == null || continueUrl.isBlank()) {
            return null;
        }
        String marker = "/p/" + projectSlug + "/oauth2/";
        if (continueUrl.startsWith(marker)) {
            return continueUrl; // relativo, mismo origen
        }
        if ((continueUrl.startsWith("http://") || continueUrl.startsWith("https://")) && continueUrl.contains(marker)) {
            try {
                URL url = new URL(continueUrl);
                if (url.getHost() != null && url.getHost().equals(request.getServerName())) {
                    return continueUrl; // absoluto en el host del API
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
