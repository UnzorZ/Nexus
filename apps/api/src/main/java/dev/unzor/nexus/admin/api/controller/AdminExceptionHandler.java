package dev.unzor.nexus.admin.api.controller;

import dev.unzor.nexus.admin.domain.exception.NexusAccountEmailAlreadyExistsException;
import dev.unzor.nexus.admin.domain.exception.RegistrationClosedException;
import dev.unzor.nexus.admin.domain.exception.SessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice(basePackageClasses = NexusAccountController.class)
class AdminExceptionHandler {

    @ExceptionHandler(NexusAccountEmailAlreadyExistsException.class)
    ResponseEntity<ProblemDetail> handleDuplicateEmail(NexusAccountEmailAlreadyExistsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A Nexus account already exists for this email."
        );
        problem.setTitle("Conflict");
        problem.setProperty("code", "conflict");
        problem.setProperty("email", exception.getEmail());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password."
        );
        problem.setTitle("Unauthorized");
        problem.setProperty("code", "invalid_credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    /**
     * Cuenta suspendida o desactivada: el {@code DaoAuthenticationProvider} lanza
     * {@link DisabledException} cuando el principal no está habilitado (estado
     * distinto de {@code ACTIVE}). Se devuelve un 403 con un código estable para
     * que el frontend pueda redirigir a la página dedicada.
     */
    @ExceptionHandler(DisabledException.class)
    ResponseEntity<ProblemDetail> handleAccountSuspended(DisabledException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Esta cuenta está suspendida."
        );
        problem.setTitle("Forbidden");
        problem.setProperty("code", "account_suspended");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(RegistrationClosedException.class)
    ResponseEntity<ProblemDetail> handleRegistrationClosed(RegistrationClosedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
        problem.setTitle("Conflict");
        problem.setProperty("code", "registration_closed");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<ProblemDetail> handleSessionNotFound(SessionNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Session not found."
        );
        problem.setTitle("Not found");
        problem.setProperty("code", "session_not_found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(AdminExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation failed");
        problem.setProperty("code", "validation_error");
        return ResponseEntity.badRequest().body(problem);
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
