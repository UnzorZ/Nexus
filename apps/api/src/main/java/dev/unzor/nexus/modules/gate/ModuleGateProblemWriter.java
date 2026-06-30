package dev.unzor.nexus.modules.gate;

import dev.unzor.nexus.modules.domain.enums.NexusModule;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

/**
 * Escribe la respuesta {@code 403 module_disabled} (spec §11) como
 * {@code application/problem+json}, con el {@code traceId} desde MDC (puesto por
 * {@code TraceIdFilter}).
 *
 * <p>Répplica local de {@code ProjectApiProblemWriter} (módulo {@code apikeys}):
 * el gate vive en {@code modules} y no puede depender de aquél sin publicar otro
 * named interface de {@code apikeys}; la forma canónica del spec (§11) exige
 * además el campo {@code traceId}, que aquél no añade.</p>
 */
@Component
public class ModuleGateProblemWriter {

    private static final URI TYPE = URI.create("https://docs.nexus.local/errors/module-disabled");

    private final ObjectMapper objectMapper;

    public ModuleGateProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeDisabled(HttpServletResponse response, NexusModule module) {
        try {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.FORBIDDEN,
                    "The " + module.key() + " module is disabled for this project.");
            problem.setType(TYPE);
            problem.setTitle("Module disabled");
            problem.setProperty("code", "module_disabled");
            String traceId = MDC.get("traceId");
            if (traceId != null) {
                problem.setProperty("traceId", traceId);
            }
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/problem+json");
            response.getWriter().write(objectMapper.writeValueAsString(problem));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write module_disabled response", exception);
        }
    }
}
