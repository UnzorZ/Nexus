package dev.unzor.nexus.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pone el trace ID en MDC para toda la petición, incluidas las cadenas de
 * seguridad (auditoría ADR-0006). Se ejecuta antes que el FilterChainProxy de
 * Spring Security (DEFAULT_FILTER_ORDER = -100) para que los filtros de auth
 * (p. ej. el de API key) también vean el trace ID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request);

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String incomingTraceId = request.getHeader(TRACE_ID_HEADER);

        if (incomingTraceId == null || incomingTraceId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String trimmedTraceId = incomingTraceId.trim();

        if (trimmedTraceId.length() > 128) {
            return UUID.randomUUID().toString();
        }

        return trimmedTraceId;
    }
}
