package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.application.service.ProjectUserMfaService;
import dev.unzor.nexus.identity.application.service.ProjectUserMfaService.Enrollment;
import dev.unzor.nexus.identity.application.service.ProjectUserMfaService.MfaStatus;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * API JSON de gestión self-service de MFA TOTP para el usuario final autenticado
 * ({@code /api/p/{slug}/mfa/**}): inscripción (QR + secret), confirmación (emite
 * recovery codes), desactivación y estado. Delega en {@link ProjectUserMfaService}.
 */
@RestController
@RequestMapping("/api/p/{projectSlug}/mfa")
class ProjectEndUserMfaController {

    private final ProjectSlugResolver slugResolver;
    private final ProjectUserMfaService mfaService;

    ProjectEndUserMfaController(ProjectSlugResolver slugResolver, ProjectUserMfaService mfaService) {
        this.slugResolver = slugResolver;
        this.mfaService = mfaService;
    }

    @PostMapping("/enroll")
    Enrollment enroll(@PathVariable String projectSlug, @AuthenticationPrincipal ProjectUserPrincipal principal) {
        resolve(projectSlug);
        try {
            return mfaService.beginEnrollment(principal.projectId(), principal.userId());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    record VerifyRequest(String code) {
    }

    @PostMapping("/enroll/verify")
    ResponseEntity<Map<String, List<String>>> confirmEnrollment(
            @PathVariable String projectSlug,
            @AuthenticationPrincipal ProjectUserPrincipal principal,
            @RequestBody VerifyRequest request
    ) {
        resolve(projectSlug);
        try {
            List<String> codes = mfaService.confirmEnrollment(principal.projectId(), principal.userId(), request.code());
            return ResponseEntity.ok(Map.of("recoveryCodes", codes));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_code");
        }
    }

    @PostMapping("/disable")
    ResponseEntity<Void> disable(
            @PathVariable String projectSlug,
            @AuthenticationPrincipal ProjectUserPrincipal principal,
            @RequestBody VerifyRequest request
    ) {
        resolve(projectSlug);
        try {
            mfaService.disable(principal.projectId(), principal.userId(), request.code());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_code");
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    MfaStatus status(@PathVariable String projectSlug, @AuthenticationPrincipal ProjectUserPrincipal principal) {
        resolve(projectSlug);
        return mfaService.status(principal.projectId(), principal.userId());
    }

    private void resolve(String projectSlug) {
        try {
            slugResolver.resolve(projectSlug);
        } catch (ProjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }
}
