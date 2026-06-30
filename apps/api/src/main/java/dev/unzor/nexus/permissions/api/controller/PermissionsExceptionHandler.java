package dev.unzor.nexus.permissions.api.controller;

import dev.unzor.nexus.permissions.domain.exception.PermissionAlreadyExistsException;
import dev.unzor.nexus.permissions.domain.exception.PermissionNotFoundException;
import dev.unzor.nexus.permissions.domain.exception.RoleAlreadyExistsException;
import dev.unzor.nexus.permissions.domain.exception.RoleNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Traduce las excepciones del módulo {@code permissions} a respuestas RFC 7807
 * con una propiedad {@code code}. Está acotado al paquete del controlador, así
 * que cubre tanto permisos como roles.
 */
@RestControllerAdvice(basePackageClasses = ProjectPermissionsController.class)
class PermissionsExceptionHandler {

    @ExceptionHandler(PermissionNotFoundException.class)
    ResponseEntity<ProblemDetail> handlePermissionNotFound(PermissionNotFoundException exception) {
        return notFound(exception.getMessage());
    }

    @ExceptionHandler(RoleNotFoundException.class)
    ResponseEntity<ProblemDetail> handleRoleNotFound(RoleNotFoundException exception) {
        return notFound(exception.getMessage());
    }

    @ExceptionHandler({PermissionAlreadyExistsException.class, RoleAlreadyExistsException.class})
    ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Conflict");
        problem.setProperty("code", "conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
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
        return notFound("Project not found.");
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

    private static ResponseEntity<ProblemDetail> notFound(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail);
        problem.setTitle("Not found");
        problem.setProperty("code", "resource_not_found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
