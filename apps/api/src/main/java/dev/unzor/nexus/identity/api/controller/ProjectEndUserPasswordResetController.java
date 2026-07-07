package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.application.service.ProjectUserPasswordResetService;
import dev.unzor.nexus.identity.domain.exception.InvalidPasswordResetTokenException;
import dev.unzor.nexus.identity.domain.exception.WeakPasswordException;
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
 * API JSON de reseteo self-service de contraseña del usuario final. Delega en
 * {@link ProjectUserPasswordResetService} (que al confirmar revoca sesiones + bump de
 * authz_version). Reemplaza al {@code ProjectPasswordResetController} Thymeleaf.
 * Anti-enumeración: el request responde siempre igual exista o no la cuenta.
 */
@RestController
@RequestMapping("/api/p/{projectSlug}")
class ProjectEndUserPasswordResetController {

    private final ProjectSlugResolver slugResolver;
    private final ProjectUserPasswordResetService resetService;

    ProjectEndUserPasswordResetController(ProjectSlugResolver slugResolver,
            ProjectUserPasswordResetService resetService) {
        this.slugResolver = slugResolver;
        this.resetService = resetService;
    }

    record ResetRequest(String email) {
    }

    record ConfirmRequest(String token, String newPassword) {
    }

    @PostMapping("/password-reset")
    ResponseEntity<Map<String, String>> request(
            @PathVariable String projectSlug, @RequestBody ResetRequest request) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        resetService.requestReset(context.projectId(), request.email()); // anti-enumeración: siempre 200
        return ResponseEntity.ok(Map.of("status", "sent"));
    }

    @PostMapping("/password-reset/confirm")
    ResponseEntity<Map<String, String>> confirm(
            @PathVariable String projectSlug, @RequestBody ConfirmRequest request) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        try {
            resetService.confirm(context.projectId(), request.token(), request.newPassword());
        } catch (InvalidPasswordResetTokenException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "invalid_token"));
        } catch (WeakPasswordException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "weak_password"));
        }
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    private ProjectAuthenticationContext resolve(String projectSlug) {
        try {
            return slugResolver.resolve(projectSlug);
        } catch (ProjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }
}
