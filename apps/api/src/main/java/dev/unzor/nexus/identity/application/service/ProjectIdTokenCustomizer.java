package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.permissions.application.dto.EffectiveAuthorities;
import dev.unzor.nexus.permissions.application.service.EffectiveAuthoritiesService;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Añade los claims {@code project_id}, {@code authz_version}, {@code permissions}
 * y {@code roles} a los access/ID tokens emitidos para usuarios finales. Lee el
 * issuer resuelto por SAS ({@code {origin}/p/{slug}}), resuelve el proyecto desde
 * el slug y, si el principal es un {@link ProjectUserPrincipal}, incrusta los
 * claims.
 *
 * <p>{@code permissions} contiene las claves de permiso efectivas del usuario
 * (comodines {@code orders.*} / {@code *} verbatim, ADR-0003); un resource server
 * que valide el JWT localmente puede autorizar a partir de ellas. {@code roles}
 * contiene las claves de los roles asignados (p. ej. {@code admin}), para que un
 * cliente autorice por rol sin expandir permisos. Se emiten tanto en el access
 * token como en el ID token (sin filtro por tipo de token); {@code /userinfo} los
 * reexpone vía el {@code userInfoMapper}.</p>
 *
 * <p>{@code amr} refleja los métodos de autenticación reales de la sesión, derivados
 * de los {@link FactorGrantedAuthority} del principal ({@code pwd} por contraseña,
 * {@code otp} por TOTP y el resto verbatim en minúsculas, p. ej. {@code google});
 * {@code acr} vale {@code mfa} cuando hubo segundo factor TOTP y, si no, el factor
 * primario. Ambos se emiten en access e ID token y se omiten si la sesión no lleva
 * factores (no se fabrican).</p>
 *
 * <p>No añade nada para flujos sin usuario final (p. ej. client credentials) ni
 * cuando el issuer no corresponde a un proyecto. El claim {@code iss} lo fija el
 * framework; aquí sólo se enriquecen los claims de proyecto. Ver
 * {@code docs/auth/oidc-claims.md} para el catálogo completo de claims.</p>
 */
@Component
public class ProjectIdTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final Logger log = LoggerFactory.getLogger(ProjectIdTokenCustomizer.class);

    private final ProjectSlugResolver projectSlugResolver;
    private final EffectiveAuthoritiesService effectiveAuthoritiesService;

    public ProjectIdTokenCustomizer(ProjectSlugResolver projectSlugResolver,
                                    EffectiveAuthoritiesService effectiveAuthoritiesService) {
        this.projectSlugResolver = projectSlugResolver;
        this.effectiveAuthoritiesService = effectiveAuthoritiesService;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (context.getTokenType() == null || context.getAuthorizationServerContext() == null) {
            return;
        }
        String issuer = context.getAuthorizationServerContext().getIssuer();
        String slug = extractSlug(issuer);
        if (slug == null) {
            return;
        }

        final UUID realmProjectId;
        try {
            ProjectAuthenticationContext project = projectSlugResolver.resolve(slug);
            realmProjectId = project.projectId();
        } catch (ProjectNotFoundException unknownProject) {
            return;
        } catch (ProjectNotOperationalException inactiveProject) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_GRANT,
                    "The issuer project is not operational.",
                    null), inactiveProject);
        }

        Authentication principalAuth = context.getPrincipal();
        Object principal = (principalAuth == null) ? null : principalAuth.getPrincipal();
        if (!(principal instanceof ProjectUserPrincipal pup)) {
            // Flujo sin usuario final (client credentials, etc.): no añadimos claims de usuario.
            return;
        }

        // Aislamiento entre realms (remediación de auditoría, hallazgo crítico): el
        // principal en sesión debe pertenecer al realm cuyo token se emite. Un mismatch
        // ABORTA la emisión (no la degrada): un token con iss de B y sujeto autenticado
        // en A no debe producirse bajo ningún concepto. El ProjectRealmIsolationFilter y
        // el CompositeRegisteredClientRepository ya bloquean antes (authorize cross-realm
        // y cliente cross-realm); este throw es la última línea de defensa al codificar.
        if (!realmProjectId.equals(pup.projectId())) {
            log.warn("Refusing cross-realm token issuance: principal project {} != issuer realm {}.",
                    pup.projectId(), realmProjectId);
            throw new IllegalStateException("Cross-realm token issuance refused");
        }

        context.getClaims().claim("project_id", pup.projectId().toString());
        context.getClaims().claim("authz_version", pup.authzVersion());

        // permissions: claves de permiso efectivas (comodines verbatim, ADR-0003). Se copian
        // a un ArrayList para una serialización Jackson determinista en oauth2_authorization.
        EffectiveAuthorities authorities = effectiveAuthoritiesService.forUser(pup.projectId(), pup.userId());
        context.getClaims().claim("permissions", new ArrayList<>(authorities.permissionKeys()));

        // roles: claves de los roles asignados al usuario (p. ej. "admin"). Permite que un
        // cliente autorice por rol (¿es admin de la app?) sin expandir permisos. Lista vacía
        // si el usuario no tiene roles.
        context.getClaims().claim("roles", new ArrayList<>(authorities.roleKeys()));

        // amr: métodos de autenticación reales de la sesión, derivados de los factores
        // del principal (no se asume contraseña como primario). acr: "mfa" con segundo
        // factor TOTP; en otro caso, el factor primario. Sin factores, no se emiten.
        List<String> factors = factors(principalAuth);
        if (!factors.isEmpty()) {
            context.getClaims().claim("amr", factors);
            context.getClaims().claim("acr", factors.contains("otp") ? "mfa" : factors.get(0));
        }
    }

    /**
     * Extrae los factores {@link FactorGrantedAuthority} del {@link Authentication} y
     * los mapea a valores OIDC {@code amr}: quita el prefijo {@code FACTOR_} de la
     * authority, pasa a minúsculas y traduce {@code password} → {@code pwd} y
     * {@code totp} → {@code otp}; el resto pasa verbatim (p. ej. {@code google}).
     */
    private static List<String> factors(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return List.of();
        }
        List<String> amr = new ArrayList<>();
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (a instanceof FactorGrantedAuthority) {
                String factor = a.getAuthority();
                if (factor.startsWith("FACTOR_")) {
                    factor = factor.substring("FACTOR_".length());
                }
                factor = factor.toLowerCase(Locale.ROOT);
                if ("password".equals(factor)) {
                    factor = "pwd";
                } else if ("totp".equals(factor)) {
                    factor = "otp";
                }
                amr.add(factor);
            }
        }
        return amr;
    }

    /** Extrae el slug del issuer {@code {origin}/p/{slug}} (o de una URL con ese sufijo). */
    private static String extractSlug(String issuer) {
        if (issuer == null) {
            return null;
        }
        int idx = issuer.lastIndexOf("/p/");
        if (idx < 0) {
            return null;
        }
        return issuer.substring(idx + 3);
    }
}
