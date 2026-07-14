package dev.unzor.nexus.sdk.security;

import dev.unzor.nexus.sdk.PermissionMatcher;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * SpEL bean {@code @perm} para autorización por permisos en el resource server
 * (M7 pattern). Lee el claim {@code permissions} del {@link Jwt} principal y lo
 * resuelve con {@link PermissionMatcher} (comodines verbatim). Registrada como
 * bean por {@code NexusSecurityAutoConfiguration} (el starter no se compone-escanea).
 *
 * <pre>
 *   &#64;PreAuthorize("@perm.has(authentication, 'orders.read')")
 *   public List&lt;Order&gt; orders() { ... }
 * </pre>
 *
 * <p>Es la autorización <em>optimista</em> por token (válida hasta {@code exp}).
 * Para una decisión fresca/autoritativa fuera del hilo de la petición, usar
 * {@code nexusClient.permissions().can(userId, key)} (snapshot cacheado).</p>
 */
public class NexusPermissionService {

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
