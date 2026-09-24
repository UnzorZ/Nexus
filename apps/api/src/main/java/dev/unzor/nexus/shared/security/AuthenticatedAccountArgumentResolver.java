package dev.unzor.nexus.shared.security;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolver for {@code @AuthenticationPrincipal AuthenticatedAccount} arguments.
 *
 * <p>A controller method that asks for the authenticated account must never receive
 * {@code null}: a request that reaches the handler without an {@link AuthenticatedAccount}
 * principal (expired Redis session, anonymous authentication, a principal of another
 * type) used to fail later with a NullPointerException and surface as a 500. This
 * resolver answers 401 instead — the client then sends the user back to login.
 */
@Component
public class AuthenticatedAccountArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(AuthenticatedAccount.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        return requiredAccount();
    }

    /** The current authenticated account, or a 401 when the session does not carry one. */
    public static AuthenticatedAccount requiredAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedAccount account) {
            return account;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "The session does not identify an account.");
    }
}
