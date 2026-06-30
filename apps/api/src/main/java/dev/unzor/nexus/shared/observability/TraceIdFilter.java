package dev.unzor.nexus.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
 * <p>
 * Además deja en MDC la IP del cliente y el User-Agent, que las factorías de
 * {@code AuditEvent} leen para enriquecer los eventos de auditoría (módulo
 * {@code audit}) sin acoplar la capa de servicio a la petición HTTP.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String IP_MDC_KEY = "ip";
    private static final String USER_AGENT_MDC_KEY = "userAgent";
    private static final int USER_AGENT_MAX_LENGTH = 255;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request);

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        MDC.put(IP_MDC_KEY, resolveClientIp(request));
        MDC.put(USER_AGENT_MDC_KEY, resolveUserAgent(request));
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
            MDC.remove(IP_MDC_KEY);
            MDC.remove(USER_AGENT_MDC_KEY);
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

    /**
     * IP del cliente: primer hop no vacío de {@code X-Forwarded-For} (típico tras
     * un proxy/túnel), o {@code getRemoteAddr} en directo. Se normaliza a IPv4:
     * extrae la IPv4 de direcciones IPv6-mapeadas ({@code ::ffff:1.2.3.4}) y
     * convierte el loopback IPv6 ({@code ::1}) a {@code 127.0.0.1}.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            for (String hop : forwarded.split(",")) {
                String trimmed = hop.trim();
                if (!trimmed.isEmpty()) {
                    return toIpv4(trimmed);
                }
            }
        }
        return toIpv4(request.getRemoteAddr());
    }

    private static String toIpv4(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isLoopbackAddress()) {
                return "127.0.0.1";
            }
            if (addr instanceof Inet4Address) {
                return addr.getHostAddress();
            }
            if (addr instanceof Inet6Address v6) {
                byte[] b = v6.getAddress();
                boolean mapped = b.length == 16;
                for (int i = 0; i < 10 && mapped; i++) {
                    if (b[i] != 0) {
                        mapped = false;
                    }
                }
                // IPv4-mapped IPv6 (::ffff:a.b.c.d): 80 bits a 0 + 16 a 1.
                if (mapped && b[10] == (byte) 0xff && b[11] == (byte) 0xff) {
                    return (b[12] & 0xff) + "." + (b[13] & 0xff) + "."
                            + (b[14] & 0xff) + "." + (b[15] & 0xff);
                }
            }
        } catch (UnknownHostException ignored) {
            // IP no parseable: se devuelve tal cual.
        }
        return ip;
    }

    private String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader(USER_AGENT_HEADER);
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > USER_AGENT_MAX_LENGTH
                ? userAgent.substring(0, USER_AGENT_MAX_LENGTH)
                : userAgent;
    }
}
