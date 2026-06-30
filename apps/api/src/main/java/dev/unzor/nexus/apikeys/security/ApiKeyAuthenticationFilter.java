package dev.unzor.nexus.apikeys.security;

import dev.unzor.nexus.apikeys.application.events.ApiKeyAuditEvent;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyDisabledException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyExpiredException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyInvalidException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filtro de autenticación del API de proyecto ({@code /api/v1/**}): resuelve la
 * cabecera {@code X-Nexus-Api-Key} a un proyecto vía {@link ApiKeyResolver} y, si
 * es válida, fija el {@code SecurityContext} (principal {@link ResolvedApiKey}).
 * Si falta, no coincide, está deshabilitada o expirada, escribe el error §11
 * correspondiente y emite un {@link ApiKeyAuditEvent} de rechazo (ADR-0004).
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Nexus-Api-Key";

    private final ApiKeyResolver resolver;
    private final ProjectApiProblemWriter problemWriter;
    private final ApplicationEventPublisher eventPublisher;

    public ApiKeyAuthenticationFilter(
            ApiKeyResolver resolver,
            ProjectApiProblemWriter problemWriter,
            ApplicationEventPublisher eventPublisher
    ) {
        this.resolver = resolver;
        this.problemWriter = problemWriter;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            reject(response, HttpStatus.UNAUTHORIZED, "invalid_api_key", "Invalid API key",
                    "A valid X-Nexus-Api-Key header is required.", "api_key.auth_invalid", null, null, "missing_header");
            return;
        }
        try {
            ResolvedApiKey resolved = resolver.resolve(rawKey);
            var authentication = new UsernamePasswordAuthenticationToken(resolved, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ApiKeyInvalidException exception) {
            reject(response, HttpStatus.UNAUTHORIZED, "invalid_api_key", "Invalid API key",
                    "The API key is invalid.", "api_key.auth_invalid", null, null, "no_match");
            return;
        } catch (ApiKeyDisabledException exception) {
            reject(response, HttpStatus.UNAUTHORIZED, "api_key_disabled", "API key disabled",
                    "The API key is disabled.", "api_key.auth_disabled",
                    exception.getProjectId(), exception.getKeyId(), "disabled");
            return;
        } catch (ApiKeyExpiredException exception) {
            reject(response, HttpStatus.UNAUTHORIZED, "api_key_expired", "API key expired",
                    "The API key has expired.", "api_key.auth_expired",
                    exception.getProjectId(), exception.getKeyId(), "expired");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String title,
            String detail,
            String auditAction,
            UUID projectId,
            UUID keyId,
            String reason
    ) {
        eventPublisher.publishEvent(new ApiKeyAuditEvent(
                auditAction, projectId, keyId, "ANONYMOUS", null, reason, Map.of(), MDC.get("traceId")));
        problemWriter.write(response, status, code, title, detail);
    }
}
