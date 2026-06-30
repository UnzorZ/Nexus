package dev.unzor.nexus.apikeys.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Escribe respuestas de error del API de proyecto (spec §11) como
 * {@code application/problem+json} con una propiedad {@code code}. Lo usan el
 * filtro de autenticación y el interceptor de scopes.
 */
@Component
public class ProjectApiProblemWriter {

    private final ObjectMapper objectMapper;

    public ProjectApiProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String title, String detail) {
        try {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
            problem.setTitle(title);
            problem.setProperty("code", code);
            response.setStatus(status.value());
            response.setContentType("application/problem+json");
            response.getWriter().write(objectMapper.writeValueAsString(problem));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write API problem response", exception);
        }
    }
}
