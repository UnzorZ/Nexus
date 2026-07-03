package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.domain.exception.OauthClientIdAlreadyExistsException;
import dev.unzor.nexus.identity.domain.exception.OauthClientNotFoundException;
import dev.unzor.nexus.identity.domain.exception.ProjectUserEmailAlreadyExistsException;
import dev.unzor.nexus.identity.domain.exception.ProjectUserNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Manejo de errores del API de identidad (CRUD de ProjectUser). Mapea a la
 * forma problem+code usada por el resto del panel.
 */
@RestControllerAdvice(basePackageClasses = ProjectUsersController.class)
class IdentityExceptionHandler {

    @ExceptionHandler(ProjectUserNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ProjectUserNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Project user not found.");
        problem.setTitle("Not found");
        problem.setProperty("code", "resource_not_found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(ProjectUserEmailAlreadyExistsException.class)
    ResponseEntity<ProblemDetail> handleDuplicateEmail(ProjectUserEmailAlreadyExistsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A project user with that email already exists.");
        problem.setTitle("Conflict");
        problem.setProperty("code", "conflict");
        problem.setProperty("email", exception.getEmail());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(OauthClientNotFoundException.class)
    ResponseEntity<ProblemDetail> handleOauthClientNotFound(OauthClientNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "OAuth client not found.");
        problem.setTitle("Not found");
        problem.setProperty("code", "resource_not_found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(OauthClientIdAlreadyExistsException.class)
    ResponseEntity<ProblemDetail> handleOauthClientConflict(OauthClientIdAlreadyExistsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "An OAuth client with that client id already exists.");
        problem.setTitle("Conflict");
        problem.setProperty("code", "conflict");
        problem.setProperty("client_id", exception.getClientId());
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
}
