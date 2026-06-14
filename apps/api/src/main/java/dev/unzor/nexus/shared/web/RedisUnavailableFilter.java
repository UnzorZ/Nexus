package dev.unzor.nexus.shared.web;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Convierte los fallos inequívocos de conexión con Redis en una respuesta {@code 503}
 * con el código de error {@code redis_unavailable}.
 *
 * <p>Spring Session Redis carga y guarda la sesión dentro de su
 * {@code SessionRepositoryFilter}. Si Redis no está disponible, la excepción de
 * conexión escapa hacia arriba en la cadena de filtros; este filtro la captura y
 * responde {@code 503 Service Unavailable} (RFC 9457 {@link ProblemDetail}) en lugar de
 * dejar que la petición continúe como anónima o derive en un {@code 500} genérico.</p>
 *
 * <p>Solo se considera un fallo de Redis cuando la excepción capturada o su cadena de
 * causas contiene una señal inequívoca de Redis/Lettuce:</p>
 * <ul>
 *   <li>{@link RedisConnectionFailureException} (Spring Data Redis),</li>
 *   <li>{@link RedisConnectionException} o {@link RedisCommandTimeoutException}
 *       (Lettuce), o</li>
 *   <li>otra subclase de {@link RedisException} de Lettuce.</li>
 * </ul>
 * <p>Excepciones genéricas como {@code org.springframework.dao.QueryTimeoutException}
 * sin una causa de Redis/Lettuce <em>no</em> se tratan como {@code redis_unavailable} y
 * se propagan normalmente. El comportamiento es <em>fail-closed</em>: sin Redis, ningún
 * endpoint protegido por sesión del panel responde como autenticado ni permite acceso
 * anónimo accidental.</p>
 */
public class RedisUnavailableFilter extends OncePerRequestFilter {

    /** Código de error máquina-expuesto en el {@code ProblemDetail}. */
    public static final String ERROR_CODE = "redis_unavailable";

    private final ObjectMapper objectMapper;

    public RedisUnavailableFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            if (!isRedisUnavailable(exception)) {
                throw exception;
            }
            writeRedisUnavailable(response);
        }
    }

    /**
     * Determina si la cadena de causas contiene una señal inequívoca de fallo de Redis
     * o Lettuce. Inspecciona toda la cadena para detectar causas envueltas.
     */
    static boolean isRedisUnavailable(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RedisConnectionFailureException
                    || current instanceof RedisConnectionException
                    || current instanceof RedisCommandTimeoutException
                    || current instanceof RedisException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void writeRedisUnavailable(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The session store is unavailable. Please try again shortly."
        );
        problem.setTitle("Session store unavailable");
        problem.setProperty("code", ERROR_CODE);

        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
