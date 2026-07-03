package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.infrastructure.security.ProjectSessionAuthenticator;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Login funcional de usuarios finales por proyecto, bajo {@code /p/{projectSlug}}.
 * El GET renderiza el formulario (Thymeleaf); el POST autentica vía
 * {@link ProjectSessionAuthenticator} y, si todo va bien, establece la sesión y
 * redirige a {@code /me}. Cualquier fallo (usuario inexistente, suspendido,
 * contraseña errónea) se colapsa al mismo error genérico para no revelar si el
 * email existe (anti-enumeración).
 */
@Controller
@RequestMapping("/p/{projectSlug}")
class ProjectLoginController {

    private final ProjectSlugResolver projectSlugResolver;
    private final ProjectSessionAuthenticator sessionAuthenticator;

    ProjectLoginController(ProjectSlugResolver projectSlugResolver, ProjectSessionAuthenticator sessionAuthenticator) {
        this.projectSlugResolver = projectSlugResolver;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @GetMapping("/login")
    String loginForm(@PathVariable String projectSlug, Model model, CsrfToken csrfToken) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        model.addAttribute("projectSlug", context.projectSlug());
        model.addAttribute("projectId", context.projectId());
        model.addAttribute("csrf", csrfToken);
        return "identity/project-login";
    }

    @PostMapping("/login")
    String loginSubmit(
            @PathVariable String projectSlug,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(name = "continue", required = false) String continueUrl,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            CsrfToken csrfToken
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        try {
            sessionAuthenticator.authenticate(context.projectId(), email, password, request, response);
            return "redirect:" + safePostLoginTarget(continueUrl, context.projectSlug());
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            model.addAttribute("projectSlug", context.projectSlug());
            model.addAttribute("projectId", context.projectId());
            model.addAttribute("csrf", csrfToken);
            model.addAttribute("email", email);
            model.addAttribute("error", ProjectSessionAuthenticator.GENERIC_ERROR);
            return "identity/project-login";
        }
    }

    /**
     * Landing mínima post-login (smoke target de B1). La cadena /p/** exige
     * autenticación; el principal autenticado lo aporta la sesión creada en el
     * login. La app real de usuarios finales llega con B2 (OAuth).
     */
    @GetMapping("/me")
    String me(@PathVariable String projectSlug, Model model) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        model.addAttribute("projectSlug", context.projectSlug());
        return "identity/project-me";
    }

    /**
     * Cierre de sesión SP-initiated (B3). El GET muestra la confirmación (o la página
     * "sesión cerrada" si llega {@code ?done}); el POST invalida la sesión, limpia el
     * {@code SecurityContext} y redirige a {@code ?done}. La cadena {@code /p/...} deja
     * la ruta de logout como {@code permitAll} para que la página final sea alcanzable
     * sin sesión; el POST sigue protegido por CSRF.
     */
    @GetMapping("/logout")
    String logoutForm(
            @PathVariable String projectSlug,
            @RequestParam(name = "done", required = false) String done,
            Model model,
            CsrfToken csrfToken
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        model.addAttribute("projectSlug", context.projectSlug());
        model.addAttribute("done", done != null);
        model.addAttribute("csrf", csrfToken);
        return "identity/project-signed-out";
    }

    @PostMapping("/logout")
    String logoutSubmit(@PathVariable String projectSlug, HttpServletRequest request) {
        resolve(projectSlug); // 404 si el proyecto no existe
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return "redirect:/p/" + projectSlug + "/logout?done";
    }

    /**
     * Destino post-login: el {@code continue} sólo si apunta al mismo realm
     * ({@code /p/{slug}/...}, anti open-redirect); en caso contrario, la página /me.
     */
    private static String safePostLoginTarget(String continueUrl, String projectSlug) {
        String me = "/p/" + projectSlug + "/me";
        if (continueUrl != null && continueUrl.startsWith("/p/" + projectSlug + "/")) {
            return continueUrl;
        }
        return me;
    }

    private ProjectAuthenticationContext resolve(String projectSlug) {
        try {
            return projectSlugResolver.resolve(projectSlug);
        } catch (ProjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }
}
