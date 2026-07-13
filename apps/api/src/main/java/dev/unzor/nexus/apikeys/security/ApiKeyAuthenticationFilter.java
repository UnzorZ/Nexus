package dev.unzor.nexus.apikeys.security;

import dev.unzor.nexus.apikeys.domain.exception.ApiKeyDisabledException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyExpiredException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyInvalidException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyProjectNotOperationalException;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import dev.unzor.nexus.shared.audit.AuditEvent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Filtro de autenticación del API de proyecto ({@code /api/v1/**}). Acepta tres
 * credenciales:
 * <ul>
 *   <li>{@code X-Nexus-Instance-Token} — token efímero (ADR-0012) verificado en
 *       Redis; pensado para latidos de alta frecuencia.</li>
 *   <li>{@code X-Nexus-Api-Key} — la API key larga, verificada por
 *       {@link ApiKeyResolver} (SHA-256 en tiempo constante). Back-compat y
 *       bootstrap del handshake (el {@code register} la usa para mintear el
 *       token).</li>
 *   <li>{@code Authorization: Bearer <api-key>} — la misma API key vía cabecera
 *       estándar de Bearer. Útil para clientes que sólo pueden enviar
 *       {@code Authorization} (p. ej. un {@code scrape_config} de Prometheus con
 *       {@code bearer_token}); se resuelve idéntico al header {@code X-Nexus-Api-Key}.</li>
 * </ul>
 * </ul>
 * Si alguna es válida, fija el {@code SecurityContext} (principal
 * {@link ResolvedApiKey}). Si no llega ninguna, o es inválida, escribe el error
 * §11 y emite un {@link AuditEvent} de rechazo (ADR-0004) que el módulo
 * {@code audit} persiste.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Nexus-Api-Key";
    public static final String TOKEN_HEADER = "X-Nexus-Instance-Token";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyResolver resolver;
    private final InstanceTokenService instanceTokenService;
    private final ProjectLookupService projectLookupService;
    private final ProjectApiProblemWriter problemWriter;
    private final ApplicationEventPublisher eventPublisher;

    public ApiKeyAuthenticationFilter(
            ApiKeyResolver resolver,
            InstanceTokenService instanceTokenService,
            ProjectLookupService projectLookupService,
            ProjectApiProblemWriter problemWriter,
            ApplicationEventPublisher eventPublisher
    ) {
        this.resolver = resolver;
        this.instanceTokenService = instanceTokenService;
        this.projectLookupService = projectLookupService;
        this.problemWriter = problemWriter;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // Instance token (ADR-0012): credencial efímera de alta frecuencia.
        String token = request.getHeader(TOKEN_HEADER);
        if (token != null && !token.isBlank()) {
            Optional<ResolvedApiKey> resolved = instanceTokenService.resolve(token);
            if (resolved.isEmpty()) {
                reject(response, HttpStatus.UNAUTHORIZED, "invalid_instance_token", "Invalid instance token",
                        "The instance token is invalid or expired.", "instance_token.auth_invalid",
                        null, null, "no_match");
                return;
            }
            ResolvedApiKey instanceToken = resolved.get();
            try {
                projectLookupService.requireOperationalById(instanceToken.projectId());
            } catch (ProjectNotFoundException exception) {
                reject(response, HttpStatus.UNAUTHORIZED, "invalid_instance_token", "Invalid instance token",
                        "The instance token is invalid or expired.", "instance_token.auth_invalid",
                        instanceToken.projectId(), instanceToken.keyId(), "project_not_found");
                return;
            } catch (ProjectNotOperationalException exception) {
                rejectProjectNotOperational(response, instanceToken, "instance_token.auth_rejected");
                return;
            }
            authenticate(instanceToken, ResolvedCredential.INSTANCE_TOKEN);
            filterChain.doFilter(request, response);
            return;
        }

        // API key (back-compat + bootstrap del handshake). Se acepta vía el header
        // X-Nexus-Api-Key o, si éste falta, vía Authorization: Bearer <key> (para
        // clientes que sólo pueden enviar Authorization, p. ej. Prometheus).
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            rawKey = extractBearer(request);
        }
        if (rawKey == null || rawKey.isBlank()) {
            reject(response, HttpStatus.UNAUTHORIZED, "invalid_api_key", "Invalid API key",
                    "A valid X-Nexus-Api-Key (or Bearer) header is required.", "api_key.auth_invalid", null, null, "missing_header");
            return;
        }
        try {
            ResolvedApiKey resolved = resolver.resolve(rawKey);
            authenticate(resolved, ResolvedCredential.API_KEY);
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
        } catch (ApiKeyProjectNotOperationalException exception) {
            reject(response, HttpStatus.FORBIDDEN, "project_not_operational", "Project not operational",
                    "The project is not operational.", "api_key.auth_rejected",
                    exception.getProjectId(), exception.getKeyId(), "project_not_operational");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(ResolvedApiKey resolved, ResolvedCredential credential) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(resolved, null, List.of());
        auth.setDetails(credential);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void rejectProjectNotOperational(
            HttpServletResponse response,
            ResolvedApiKey resolved,
            String auditAction
    ) {
        reject(response, HttpStatus.FORBIDDEN, "project_not_operational", "Project not operational",
                "The project is not operational.", auditAction,
                resolved.projectId(), resolved.keyId(), "project_not_operational");
    }

    /**
     * Extrae una API key de una cabecera {@code Authorization: Bearer <key>}
     * (insensible a mayúsculas en el esquema). Devuelve {@code null} si la
     * cabecera no está o no es un Bearer válido.
     */
    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || header.length() <= BEARER_PREFIX.length()) {
            return null;
        }
        String scheme = header.substring(0, BEARER_PREFIX.length());
        if (!BEARER_PREFIX.equalsIgnoreCase(scheme)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
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
        eventPublisher.publishEvent(AuditEvent.anonymous(
                projectId, auditAction, "api_key", keyId == null ? null : keyId.toString(),
                reason));
        problemWriter.write(response, status, code, title, detail);
    }
}
