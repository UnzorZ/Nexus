package dev.unzor.nexus.registry.api.controller;

import dev.unzor.nexus.apikeys.security.RawApiKeyRequiredException;
import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.registry.domain.exception.InvalidRegistrySettingsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Traduce las excepciones del módulo {@code registry} a respuestas RFC 7807 con
 * una propiedad {@code code}. Cubre tanto el endpoint de runtime
 * ({@code /api/v1/registry/heartbeat}) como el listado del panel. Las
 * excepciones de auth/scope (invalid/disabled/expired/missing_scope) las escriben
 * el filtro y el interceptor de la cadena {@code /api/v1/**}.
 */
@RestControllerAdvice(basePackageClasses = ProjectRegistryController.class)
class RegistryExceptionHandler {

    @ExceptionHandler(RawApiKeyRequiredException.class)
    ResponseEntity<ProblemDetail> handleRawKeyRequired(RawApiKeyRequiredException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, exception.getMessage());
        problem.setTitle("Raw API key required");
        problem.setProperty("code", "raw_api_key_required");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(ProjectAccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(ProjectAccessDeniedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have access to this project.");
        problem.setTitle("Forbidden");
        problem.setProperty("code", "permission_denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    ResponseEntity<ProblemDetail> handleProjectNotFound(ProjectNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Project not found.");
        problem.setTitle("Not found");
        problem.setProperty("code", "resource_not_found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation failed");
        problem.setProperty("code", "validation_error");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(InvalidRegistrySettingsException.class)
    ResponseEntity<ProblemDetail> handleInvalidSettings(InvalidRegistrySettingsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid liveness thresholds");
        problem.setProperty("code", "validation_error");
        return ResponseEntity.badRequest().body(problem);
    }
}
