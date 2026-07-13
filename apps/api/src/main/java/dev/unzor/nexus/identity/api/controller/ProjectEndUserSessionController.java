package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.application.service.ProjectUserSessionService;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import dev.unzor.nexus.shared.security.SessionSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NO_CONTENT;

/**
 * API JSON de gestión de sesiones del usuario final ({@code /api/p/{slug}/sessions}).
 * Espejo de los endpoints de sesiones del panel: listado (sesión actual primero),
 * revocación por identificador público y revocación total.
 *
 * <p>Un usuario sólo consulta o revoca sus propias sesiones (indexadas por
 * {@code nexus.projectUserId}); una sesión ajena o inexistente se trata como 404.</p>
 */
@RestController
@RequestMapping("/api/p/{projectSlug}")
class ProjectEndUserSessionController {

    private final ProjectSlugResolver slugResolver;
    private final ProjectUserSessionService sessionService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    ProjectEndUserSessionController(
            ProjectSlugResolver slugResolver,
            ProjectUserSessionService sessionService
    ) {
        this.slugResolver = slugResolver;
        this.sessionService = sessionService;
        logoutHandler.setClearAuthentication(true);
        logoutHandler.setInvalidateHttpSession(true);
    }

    /**
     * Lista las sesiones del usuario autenticado, con la sesión actual primero y el resto
     * por último acceso descendente.
     */
    @GetMapping("/sessions")
    List<SessionSummary> listSessions(
            @PathVariable String projectSlug,
            @AuthenticationPrincipal ProjectUserPrincipal principal,
            HttpSession session
    ) {
        resolve(projectSlug);
        return sessionService.listForUser(principal.userId(), currentSessionPublicId(session));
    }

    /**
     * Revoca una sesión concreta por su identificador público. Si se revoca la sesión
     * actual, también limpia la autenticación y la cookie.
     */
    @DeleteMapping("/sessions/{publicSessionId}")
    @ResponseStatus(NO_CONTENT)
    void revokeSession(
            @PathVariable String projectSlug,
            @PathVariable UUID publicSessionId,
            @AuthenticationPrincipal ProjectUserPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        resolve(projectSlug);
        sessionService.revokeByPublicId(principal.userId(), publicSessionId);

        if (isCurrentSession(request.getSession(false), publicSessionId)) {
            logoutHandler.logout(request, response, authentication);
        }
    }

    /**
     * Revoca todas las sesiones del usuario, incluida la actual, y limpia autenticación
     * y cookie.
     */
    @DeleteMapping("/sessions")
    @ResponseStatus(NO_CONTENT)
    void revokeAllSessions(
            @PathVariable String projectSlug,
            @AuthenticationPrincipal ProjectUserPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        resolve(projectSlug);
        sessionService.revokeAll(principal.userId());
        logoutHandler.logout(request, response, authentication);
    }

    private void resolve(String projectSlug) {
        // Listar/revocar las PROPIAS sesiones y hacer logout son operaciones de teardown:
        // deben funcionar incluso si el realm está archivado/suspendido, para que un
        // usuario siempre pueda salir de un proyecto decomisionado. Por eso resolvemos
        // por existencia (resolveExisting), no por operatividad — a diferencia de /me.
        try {
            slugResolver.resolveExisting(projectSlug);
        } catch (ProjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }

    private static UUID currentSessionPublicId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(NexusSessionAttributes.SESSION_PUBLIC_ID);
        return value instanceof String text ? UUID.fromString(text) : null;
    }

    private static boolean isCurrentSession(HttpSession session, UUID publicSessionId) {
        UUID current = currentSessionPublicId(session);
        return current != null && current.equals(publicSessionId);
    }
}
