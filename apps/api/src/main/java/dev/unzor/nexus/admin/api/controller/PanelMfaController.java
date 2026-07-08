package dev.unzor.nexus.admin.api.controller;

import dev.unzor.nexus.admin.application.service.NexusAccountMfaService;
import dev.unzor.nexus.admin.application.service.NexusAccountMfaService.Enrollment;
import dev.unzor.nexus.admin.application.service.NexusAccountMfaService.MfaStatus;
import dev.unzor.nexus.admin.infrastructure.security.NexusAccountPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * API JSON de gestión self-service de MFA TOTP para la cuenta del panel autenticada
 * ({@code /api/panel/v1/mfa/**}): inscripción (QR + secret), confirmación (emite
 * recovery codes), desactivación y estado. Espejo de {@code ProjectEndUserMfaController}
 * para el panel. Delega en {@link NexusAccountMfaService}.
 */
@RestController
@RequestMapping("/api/panel/v1/mfa")
class PanelMfaController {

    private final NexusAccountMfaService mfaService;

    PanelMfaController(NexusAccountMfaService mfaService) {
        this.mfaService = mfaService;
    }

    @PostMapping("/enroll")
    Enrollment enroll(@AuthenticationPrincipal NexusAccountPrincipal principal) {
        try {
            return mfaService.beginEnrollment(principal.accountId());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    record VerifyRequest(String code) {
    }

    @PostMapping("/enroll/verify")
    ResponseEntity<Map<String, List<String>>> confirmEnrollment(
            @AuthenticationPrincipal NexusAccountPrincipal principal,
            @RequestBody VerifyRequest request
    ) {
        try {
            List<String> codes = mfaService.confirmEnrollment(principal.accountId(), request.code());
            return ResponseEntity.ok(Map.of("recoveryCodes", codes));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_code");
        }
    }

    @PostMapping("/disable")
    ResponseEntity<Void> disable(
            @AuthenticationPrincipal NexusAccountPrincipal principal,
            @RequestBody VerifyRequest request
    ) {
        try {
            mfaService.disable(principal.accountId(), request.code());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_code");
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    MfaStatus status(@AuthenticationPrincipal NexusAccountPrincipal principal) {
        return mfaService.status(principal.accountId());
    }
}
