package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.application.service.ProjectUserEmailVerificationService;
import dev.unzor.nexus.identity.application.service.RegisterProjectUserService;
import dev.unzor.nexus.identity.domain.exception.InvalidEmailVerificationTokenException;
import dev.unzor.nexus.identity.domain.exception.ProjectUserEmailAlreadyExistsException;
import dev.unzor.nexus.identity.domain.exception.PublicRegistrationDisabledException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * API JSON de alta pública y verificación de email del usuario final. Delega en
 * {@link RegisterProjectUserService} y {@link ProjectUserEmailVerificationService}.
 * Reemplaza al {@code ProjectRegistrationController} Thymeleaf. Anti-enumeración: el
 * resend y el register-conflict no revelan existencia de cuenta más de lo inevitable.
 */
@RestController
@RequestMapping("/api/p/{projectSlug}")
class ProjectEndUserRegistrationController {

    private final ProjectSlugResolver slugResolver;
    private final RegisterProjectUserService registerService;
    private final ProjectUserEmailVerificationService verificationService;

    ProjectEndUserRegistrationController(
            ProjectSlugResolver slugResolver,
            RegisterProjectUserService registerService,
            ProjectUserEmailVerificationService verificationService
    ) {
        this.slugResolver = slugResolver;
        this.registerService = registerService;
        this.verificationService = verificationService;
    }

    record RegisterRequest(String email, String password, String displayName, String username) {
    }

    record VerifyEmailRequest(String token) {
    }

    record ResendRequest(String email) {
    }

    @PostMapping("/register")
    ResponseEntity<Map<String, String>> register(
            @PathVariable String projectSlug, @RequestBody RegisterRequest request) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        String displayName = (request.displayName() == null || request.displayName().isBlank())
                ? request.email() : request.displayName();
        try {
            registerService.register(
                    context.projectId(), request.email(), request.password(), displayName, request.username());
        } catch (PublicRegistrationDisabledException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (ProjectUserEmailAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "email_exists"));
        }
        return ResponseEntity.ok(Map.of("status", "verification_email_sent", "email", request.email().trim()));
    }

    @PostMapping("/verify-email")
    ResponseEntity<Map<String, String>> verifyEmail(
            @PathVariable String projectSlug, @RequestBody VerifyEmailRequest request) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        try {
            verificationService.verify(context.projectId(), request.token());
        } catch (InvalidEmailVerificationTokenException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "invalid_token"));
        }
        return ResponseEntity.ok(Map.of("status", "verified"));
    }

    @PostMapping("/verify-email/resend")
    ResponseEntity<Map<String, String>> resend(
            @PathVariable String projectSlug, @RequestBody ResendRequest request) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        // Anti-enumeración: respuesta idéntica exista o no la cuenta.
        verificationService.resend(context.projectId(), request.email());
        return ResponseEntity.ok(Map.of("status", "sent"));
    }

    private ProjectAuthenticationContext resolve(String projectSlug) {
        try {
            return slugResolver.resolve(projectSlug);
        } catch (ProjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }
}
