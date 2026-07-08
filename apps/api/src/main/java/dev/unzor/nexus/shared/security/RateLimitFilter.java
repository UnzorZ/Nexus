package dev.unzor.nexus.shared.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Rate-limit per-IP de los endpoints de auth pública. Se ejecuta antes que la cadena
 * de Spring Security (registrado con la máxima precedencia vía
 * {@link RateLimitConfiguration#rateLimitFilterRegistration}) y rechaza con
 * {@code 429 Too Many Requests} (+ cabecera {@code Retry-After}) cuando se agota el
 * bucket del cliente para el tier correspondiente.
 *
 * <p>Resiliencia: ante cualquier fallo inesperado {@code falla abierto} (permite la
 * petición y logea) para no bloquear todo el sistema por un error del limitador. Las
 * peticiones que no casan con ninguna regla (método/ruta no listados) pasan sin
 * consumir tokens.
 *
 * <p>No es un bean de Spring: se instancia con {@code new} en el
 * {@code FilterRegistrationBean}, igual que {@link SameSiteCsrfCookieFilter}, para
 * evitar el proxy CGLIB de Modulith sobre {@code GenericFilterBean} (el limitador
 * se registra una sola vez y con orden controlado).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties properties;
    private final RateLimitBucketStore store;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** (método, patrón) → tier, en orden de evaluación. */
    private final List<Rule> rules = List.of(
            // AUTH: login / MFA / token (defensa contra fuerza bruta)
            rule("POST", "/api/panel/v1/session/login", RateLimitTier.AUTH),
            rule("POST", "/api/panel/v1/session/login/mfa", RateLimitTier.AUTH),
            rule("POST", "/api/p/*/login", RateLimitTier.AUTH),
            rule("POST", "/api/p/*/login/mfa", RateLimitTier.AUTH),
            rule("POST", "/oauth2/token", RateLimitTier.AUTH),
            rule("POST", "/p/*/oauth2/token", RateLimitTier.AUTH),
            // GENERAL: registro / verificación / reset (defensa contra abuso y enumeración)
            rule("POST", "/api/panel/v1/accounts", RateLimitTier.GENERAL),
            rule("POST", "/api/p/*/register", RateLimitTier.GENERAL),
            rule("POST", "/api/p/*/verify-email", RateLimitTier.GENERAL),
            rule("POST", "/api/p/*/verify-email/resend", RateLimitTier.GENERAL),
            rule("POST", "/api/p/*/password-reset", RateLimitTier.GENERAL),
            rule("POST", "/api/p/*/password-reset/confirm", RateLimitTier.GENERAL)
    );

    RateLimitFilter(RateLimitProperties properties, RateLimitBucketStore store) {
        this.properties = properties;
        this.store = store;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Rule matched = match(request);
            if (matched == null) {
                filterChain.doFilter(request, response);
                return;
            }
            String clientIp = resolveClientIp(request);
            String key = matched.tier() + ":" + clientIp;
            Bucket bucket = store.bucketFor(key, matched.tier());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                filterChain.doFilter(request, response);
            } else {
                reject(response, retryAfterSeconds(probe));
            }
        } catch (RuntimeException failure) {
            // Fail open: un error del limitador nunca debe tirar el sistema.
            log.warn("Rate-limit filter failed open for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), failure.toString());
            filterChain.doFilter(request, response);
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    private Rule match(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        for (Rule rule : rules) {
            if (rule.method().equalsIgnoreCase(method) && pathMatcher.match(rule.pattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.trustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private static long retryAfterSeconds(ConsumptionProbe probe) {
        long seconds = (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0);
        return Math.max(1, seconds);
    }

    private static void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"rate_limited\",\"retry_after_seconds\":" + retryAfterSeconds + "}");
    }

    private static Rule rule(String method, String pattern, RateLimitTier tier) {
        return new Rule(method, pattern, tier);
    }

    private record Rule(String method, String pattern, RateLimitTier tier) {}
}
