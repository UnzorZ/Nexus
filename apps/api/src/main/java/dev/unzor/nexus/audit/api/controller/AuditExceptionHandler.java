package dev.unzor.nexus.audit.api.controller;

import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones del módulo {@code audit} a respuestas RFC 7807 con una
 * propiedad {@code code}. Reutiliza las excepciones publicadas de
 * {@code projects.domain.exception} (403/404), igual que el resto de módulos.
 */
@RestControllerAdvice(basePackageClasses = ProjectAuditController.class)
class AuditExceptionHandler {

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
}
