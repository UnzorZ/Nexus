package dev.unzor.nexus.apikeys.infrastructure.interceptor;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import dev.unzor.nexus.apikeys.api.ScopeFree;
import dev.unzor.nexus.apikeys.security.ProjectApiProblemWriter;
import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Autorización por scope sobre {@code /api/v1/**} en régimen <b>deny-by-default</b>
 * (auditoría M5): un handler SIN {@link RequiredScope} NI {@link ScopeFree} se
 * deniega con {@code 403 scope_required} — así ningún endpoint del API runtime
 * queda abierto a todas las keys por omisión. {@code @ScopeFree} abre
 * explícitamente a cualquier key autenticada (p. ej. {@code /api/v1/whoami});
 * {@code @RequiredScope} exige el scope (o {@code *}) contra la API key resuelta.
 * La autenticación la hace el filtro; esto es la capa de autorización (necesita el
 * handler ya resuelto).
 */
@Component
public class RequiredScopeInterceptor implements HandlerInterceptor {

    private final ProjectApiProblemWriter problemWriter;

    public RequiredScopeInterceptor(ProjectApiProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (handlerMethod.getMethodAnnotation(ScopeFree.class) != null) {
            return true; // abierto a cualquier API key autenticada
        }
        RequiredScope required = handlerMethod.getMethodAnnotation(RequiredScope.class);
        if (required == null) {
            // Deny-by-default: endpoint /api/v1/** sin scope declarado.
            problemWriter.write(response, HttpStatus.FORBIDDEN, "scope_required",
                    "Scope required", "This endpoint does not declare a required scope.");
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ResolvedApiKey key)) {
            return true; // el filtro de autenticación ya debería haber rechazado; defensivo.
        }
        if (key.scopes().contains(required.value()) || key.scopes().contains("*")) {
            return true;
        }
        problemWriter.write(response, HttpStatus.FORBIDDEN, "missing_scope",
                "Missing scope", "The API key is missing the required scope: " + required.value());
        return false;
    }
}
