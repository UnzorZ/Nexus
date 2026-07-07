package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.application.service.ProjectUserEmailVerificationService;
import dev.unzor.nexus.identity.application.service.RegisterProjectUserService;
import dev.unzor.nexus.identity.domain.exception.InvalidEmailVerificationTokenException;
import dev.unzor.nexus.identity.domain.exception.ProjectUserEmailAlreadyExistsException;
import dev.unzor.nexus.identity.domain.exception.PublicRegistrationDisabledException;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Alta pública (self-signup) y verificación de email de usuarios finales, bajo
 * {@code /p/{projectSlug}}. El GET /register y el POST /register sólo operan si el
 * proyecto tiene habilitado el registro público (si no, 404). El GET /verify-email
 * consume el token del enlace del email. Espeja el estilo de {@link ProjectLoginController}
 * (vistas Thymeleaf, CSRF en los formularios POST).
 */
@Controller
@RequestMapping("/p/{projectSlug}")
class ProjectRegistrationController {

    private final ProjectSlugResolver slugResolver;
    private final RegisterProjectUserService registerService;
    private final ProjectUserEmailVerificationService verificationService;
    private final ProjectLookupService projectLookupService;

    ProjectRegistrationController(
            ProjectSlugResolver slugResolver,
            RegisterProjectUserService registerService,
            ProjectUserEmailVerificationService verificationService,
            ProjectLookupService projectLookupService
    ) {
        this.slugResolver = slugResolver;
        this.registerService = registerService;
        this.verificationService = verificationService;
        this.projectLookupService = projectLookupService;
    }

    @GetMapping("/register")
    String registerForm(@PathVariable String projectSlug, Model model, CsrfToken csrfToken) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        ensureRegistrationEnabled(context.projectId());
        model.addAttribute("projectSlug", context.projectSlug());
        model.addAttribute("csrf", csrfToken);
        return "identity/project-register";
    }

    @PostMapping("/register")
    String registerSubmit(
            @PathVariable String projectSlug,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(name = "displayName", required = false) String displayName,
            @RequestParam(name = "username", required = false) String username,
            Model model,
            CsrfToken csrfToken
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        try {
            registerService.register(
                    context.projectId(), email, password,
                    (displayName == null || displayName.isBlank()) ? email.trim() : displayName.trim(),
                    username);
        } catch (PublicRegistrationDisabledException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ProjectUserEmailAlreadyExistsException e) {
            model.addAttribute("projectSlug", context.projectSlug());
            model.addAttribute("csrf", csrfToken);
            model.addAttribute("email", email);
            model.addAttribute("error", "An account with that email already exists.");
            return "identity/project-register";
        }
        model.addAttribute("projectSlug", context.projectSlug());
        model.addAttribute("csrf", csrfToken);
        model.addAttribute("done", true);
        model.addAttribute("email", email.trim());
        return "identity/project-register";
    }

    @GetMapping("/verify-email")
    String verifyEmail(@PathVariable String projectSlug, @RequestParam String token, Model model) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        try {
            verificationService.verify(context.projectId(), token);
        } catch (InvalidEmailVerificationTokenException e) {
            model.addAttribute("projectSlug", context.projectSlug());
            model.addAttribute("invalid", true);
            return "identity/project-verify-email";
        }
        model.addAttribute("projectSlug", context.projectSlug());
        model.addAttribute("verified", true);
        return "identity/project-verify-email";
    }

    @PostMapping("/verify-email/resend")
    String resendVerification(
            @PathVariable String projectSlug,
            @RequestParam String email,
            Model model,
            CsrfToken csrfToken
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        // Anti-enumeración: siempre responde igual tanto si el email existe como si no.
        verificationService.resend(context.projectId(), email);
        model.addAttribute("projectSlug", context.projectSlug());
        model.addAttribute("csrf", csrfToken);
        model.addAttribute("resent", true);
        return "identity/project-register";
    }

    private void ensureRegistrationEnabled(UUID projectId) {
        if (!projectLookupService.isPublicRegistrationEnabled(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private ProjectAuthenticationContext resolve(String projectSlug) {
        try {
            return slugResolver.resolve(projectSlug);
        } catch (ProjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }
}
