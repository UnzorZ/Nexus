package dev.unzor.nexus.sdk.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;

/**
 * Convierte un JWT de Nexus en un {@link JwtAuthenticationToken}. Mapea los scopes
 * ({@code scope}/{@code scp}) a authorities {@code SCOPE_*} (igual que el converter
 * por defecto de Spring) y deja el claim {@code permissions} intacto — la
 * autorización por permisos la resuelve {@link NexusPermissionService} vía SpEL
 * {@code @perm.has(...)}, manteniendo ambos señales (scope vs permiso) separadas.
 *
 * <p>El principal es {@code jwt.getSubject()} (= el {@code sub} del token), que en
 * Nexus es el username del realm.</p>
 */
public class NexusJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = scopeConverter.convert(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
