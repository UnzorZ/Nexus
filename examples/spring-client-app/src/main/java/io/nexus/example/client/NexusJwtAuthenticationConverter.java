package io.nexus.example.client;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;

/**
 * Maps a Nexus access-token JWT into a {@link JwtAuthenticationToken}, turning
 * the standard {@code scope}/{@code scp} claim into {@code SCOPE_*} authorities
 * (the SDK default). Use this for scope-gated endpoints:
 *
 * <pre>
 *   &#64;PreAuthorize("hasAuthority('SCOPE_orders.read')")
 * </pre>
 *
 * <p>Permission-key authorization (from the {@code permissions} claim) is handled
 * separately by {@link PermissionService} via SpEL, keeping the two authorization
 * signals — OAuth scope vs. Nexus permission — cleanly separated.</p>
 */
public class NexusJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = scopeConverter.convert(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
