package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import java.security.Principal;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;

/**
 * Envuelve el introspection provider por defecto de Spring Authorization Server
 * para devolver {@code active=false} cuando la versión de autorización
 * ({@code authz_version}) del token introspectado sea <em>stale</em> — menor que
 * el valor actual del {@link dev.unzor.nexus.identity.domain.entity.ProjectUser}
 * — o cuando el usuario ya no exista.
 *
 * <p>Esto completa el contrato de revocación por cambio de rol (#22): al
 * modificar los roles/permisos de un usuario final se bumpea su
 * {@code authz_version}, y a partir de aquí todo relying party que introspecte
 * un token emitido con la versión anterior lo verá como inactivo. No afecta a
 * tokens validados localmente por firma (esos se honran hasta {@code exp}); esa
 * cobertura requeriría un resource-server propio (milestone diferido).</p>
 *
 * <p>La versión de emisión y la identidad del usuario se leen del
 * {@link ProjectUserPrincipal} persistido como atributo de la
 * {@link OAuth2Authorization} (clave {@link Principal#getName()}) — el mismo que
 * el provider de code-exchange lee. El lookup del valor actual usa una proyección
 * fina del repositorio para no hidratar la entidad completa en cada introspection
 * (que no es un path caliente).</p>
 */
public final class AuthzVersionIntrospectionAuthenticationProvider implements AuthenticationProvider {

    /** Ausencia de fila: el usuario fue borrado → el token ya no es de nadie. */
    private static final long USER_DELETED = -1L;

    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizationService;
    private final ProjectUserRepository projectUserRepository;

    public AuthzVersionIntrospectionAuthenticationProvider(
            AuthenticationProvider delegate,
            OAuth2AuthorizationService authorizationService,
            ProjectUserRepository projectUserRepository
    ) {
        this.delegate = delegate;
        this.authorizationService = authorizationService;
        this.projectUserRepository = projectUserRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        Authentication result = delegate.authenticate(authentication);
        if (!(result instanceof OAuth2TokenIntrospectionAuthenticationToken introspection)) {
            return result;
        }
        // Ya inactivo (token no encontrado / revocado / expirado): nada que revaluar.
        if (!introspection.getTokenClaims().isActive()) {
            return result;
        }

        String tokenValue = introspection.getToken();
        OAuth2Authorization authorization = authorizationService.findByToken(tokenValue, null);
        if (authorization == null) {
            return result;
        }

        ProjectUserPrincipal pup = resolveProjectUserPrincipal(authorization);
        // Flujos sin usuario final (client credentials, etc.): sin enforcement de authz_version.
        if (pup == null) {
            return result;
        }

        long current = projectUserRepository
                .findAuthzVersionByProjectIdAndId(pup.projectId(), pup.userId())
                .orElse(USER_DELETED);
        boolean stale = current == USER_DELETED || pup.authzVersion() < current;
        if (!stale) {
            return result;
        }

        // Stale (o usuario borrado): devolver inactivo, preservando el client principal.
        return new OAuth2TokenIntrospectionAuthenticationToken(
                tokenValue,
                (Authentication) introspection.getPrincipal(),
                OAuth2TokenIntrospection.builder().build()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    /**
     * SAS persiste la autenticación del resource owner como atributo de la
     * autorización bajo {@link Principal#getName()} (la misma clave que usa el
     * provider de code-exchange). Su principal es nuestro
     * {@link ProjectUserPrincipal}.
     */
    private static ProjectUserPrincipal resolveProjectUserPrincipal(OAuth2Authorization authorization) {
        Object attribute = authorization.getAttribute(Principal.class.getName());
        if (attribute instanceof Authentication auth && auth.getPrincipal() instanceof ProjectUserPrincipal pup) {
            return pup;
        }
        if (attribute instanceof ProjectUserPrincipal pup) {
            return pup;
        }
        return null;
    }
}
