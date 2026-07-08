package io.nexus.example.client;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SpEL authorization bean exposing the Nexus {@code permissions} claim to
 * method-security expressions. Use it as:
 *
 * <pre>
 *   &#64;PreAuthorize("@perm.has(authentication, 'orders.read')")
 * </pre>
 *
 * <p>It reads the verbatim permission keys (incl. wildcards) off the access-token
 * JWT and delegates glob-matching to {@link PermissionMatcher}, so an expression
 * for a concrete key passes when the token carries {@code orders.*} or
 * {@code *}. This is the primary authorization mechanism: no need to enumerate
 * "well-known" keys up front.</p>
 */
@Component("perm")
public class PermissionService {

    /**
     * @param required a concrete permission key the endpoint demands (e.g. {@code orders.read})
     * @return true if the authenticated principal's {@code permissions} claim covers it
     */
    public boolean has(Authentication authentication, String required) {
        if (authentication == null) {
            return false;
        }
        List<String> permissions = null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            permissions = jwt.getClaimAsStringList("permissions");
        }
        return PermissionMatcher.matches(permissions, required);
    }
}
