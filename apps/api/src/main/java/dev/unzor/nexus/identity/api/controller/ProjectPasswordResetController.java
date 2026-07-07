package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.application.service.ProjectUserPasswordResetService;
import dev.unzor.nexus.identity.domain.exception.InvalidPasswordResetTokenException;
import dev.unzor.nexus.identity.domain.exception.WeakPasswordException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
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
 * Reseteo self-service de contraseña bajo {@code /p/{projectSlug}}: el usuario pide
 * un enlace por email (siempre misma respuesta — anti-enumeración) y lo canjea por
 * una nueva contraseña. Espeja el estilo de {@link ProjectLoginController}.
 */
@Controller
@RequestMapping("/p/{projectSlug}")
class ProjectPasswordResetController {

    private final ProjectSlugResolver slugResolver;
    private final ProjectUserPasswordResetService resetService;

    ProjectPasswordResetController(ProjectSlugResolver slugResolver,
            ProjectUserPasswordResetService resetService) {
        this.slugResolver = slugResolver;
        this.resetService = resetService;
    }

    @GetMapping("/password-reset")
    String requestForm(@PathVariable String projectSlug, Model model, CsrfToken csrfToken) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        model.addAttribute("projectSlug", context.projectSlug());
        model.addAttribute("csrf", csrfToken);
        return "identity/project-password-reset-request";
    }

    @PostMapping("/password-reset")
    String requestSubmit(
            @PathVariable String projectSlug,
            @RequestParam String email,
            Model model,
            CsrfToken csrfToken
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        // Anti-enumeración: la respuesta es siempre la misma exista o no la cuenta.
        resetService.requestReset(context.projectId(), email);
        model.addAttribute("projectSlug", context.projectSlug());
        model.addAttribute("csrf", csrfToken);
        model.addAttribute("sent", true);
        return "identity/project-password-reset-request";
    }

    @GetMapping("/password-reset/confirm")
    String confirmForm(
            @PathVariable String projectSlug,
            @RequestParam String token,
            Model model,
            CsrfToken csrfToken
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        model.addAttribute("projectSlug", context.projectSlug());
        model.addAttribute("csrf", csrfToken);
        model.addAttribute("token", token);
        return "identity/project-password-reset-confirm";
    }

    @PostMapping("/password-reset/confirm")
    String confirmSubmit(
            @PathVariable String projectSlug,
            @RequestParam String token,
            @RequestParam String newPassword,
            Model model,
            CsrfToken csrfToken
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        try {
            resetService.confirm(context.projectId(), token, newPassword);
        } catch (InvalidPasswordResetTokenException e) {
            model.addAttribute("projectSlug", context.projectSlug());
            model.addAttribute("csrf", csrfToken);
            model.addAttribute("invalid", true);
            return "identity/project-password-reset-confirm";
        } catch (WeakPasswordException e) {
            model.addAttribute("projectSlug", context.projectSlug());
            model.addAttribute("csrf", csrfToken);
            model.addAttribute("token", token);
            model.addAttribute("weak", true);
            return "identity/project-password-reset-confirm";
        }
        model.addAttribute("projectSlug", context.projectSlug());
        model.addAttribute("done", true);
        return "identity/project-password-reset-confirm";
    }

    private ProjectAuthenticationContext resolve(String projectSlug) {
        try {
            return slugResolver.resolve(projectSlug);
        } catch (ProjectNotFoundException exception) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Project not found");
        }
    }
}
