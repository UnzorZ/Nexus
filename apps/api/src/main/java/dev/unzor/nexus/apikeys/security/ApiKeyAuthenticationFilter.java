package dev.unzor.nexus.apikeys.security;

import dev.unzor.nexus.apikeys.domain.exception.ApiKeyDisabledException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyExpiredException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyInvalidException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de autenticación del API de proyecto ({@code /api/v1/**}): resuelve la
 * cabecera {@code X-Nexus-Api-Key} a un proyecto vía {@link ApiKeyResolver} y, si
 * es válida, fija el {@code SecurityContext} (principal {@link ResolvedApiKey}).
 * Si falta, no coincide, está deshabilitada o expirada, escribe el error §11
 * correspondiente y corta la cadena.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Nexus-Api-Key";

    private final ApiKeyResolver resolver;
    private final ProjectApiProblemWriter problemWriter;

    public ApiKeyAuthenticationFilter(ApiKeyResolver resolver, ProjectApiProblemWriter problemWriter) {
        this.resolver = resolver;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            problemWriter.write(response, HttpStatus.UNAUTHORIZED, "invalid_api_key",
                    "Invalid API key", "A valid X-Nexus-Api-Key header is required.");
            return;
        }
        try {
            ResolvedApiKey resolved = resolver.resolve(rawKey);
            var authentication = new UsernamePasswordAuthenticationToken(resolved, null, java.util.List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ApiKeyInvalidException exception) {
            problemWriter.write(response, HttpStatus.UNAUTHORIZED, "invalid_api_key",
                    "Invalid API key", "The API key is invalid.");
            return;
        } catch (ApiKeyDisabledException exception) {
            problemWriter.write(response, HttpStatus.UNAUTHORIZED, "api_key_disabled",
                    "API key disabled", "The API key is disabled.");
            return;
        } catch (ApiKeyExpiredException exception) {
            problemWriter.write(response, HttpStatus.UNAUTHORIZED, "api_key_expired",
                    "API key expired", "The API key has expired.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
