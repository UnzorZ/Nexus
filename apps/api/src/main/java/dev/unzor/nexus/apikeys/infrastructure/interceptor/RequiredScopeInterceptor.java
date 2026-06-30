package dev.unzor.nexus.apikeys.infrastructure.interceptor;

import dev.unzor.nexus.apikeys.api.RequiredScope;
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
 * Autorización por scope sobre {@code /api/v1/**}: comprueba el
 * {@link RequiredScope} del handler contra los scopes de la API key resuelta. Si
 * falta, responde {@code 403 missing_scope}. La autenticación la hace el filtro;
 * esto es la capa de autorización (necesita el handler ya resuelto).
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
        RequiredScope required = handlerMethod.getMethodAnnotation(RequiredScope.class);
        if (required == null) {
            return true;
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
