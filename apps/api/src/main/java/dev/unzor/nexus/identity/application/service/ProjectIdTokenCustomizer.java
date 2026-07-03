package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Añade los claims {@code project_id} y {@code authz_version} a los access/ID
 * tokens emitidos para usuarios finales (spec §15.3). Lee el issuer resuelto por
 * SAS ({@code {origin}/p/{slug}}), resuelve el proyecto desde el slug y, si el
 * principal es un {@link ProjectUserPrincipal}, incrusta los claims.
 *
 * <p>No añade nada para flujos sin usuario final (p. ej. client credentials) ni
 * cuando el issuer no corresponde a un proyecto. El claim {@code iss} lo fija el
 * framework; aquí sólo se enriquecen los claims de proyecto.</p>
 */
@Component
public class ProjectIdTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final ProjectSlugResolver projectSlugResolver;

    public ProjectIdTokenCustomizer(ProjectSlugResolver projectSlugResolver) {
        this.projectSlugResolver = projectSlugResolver;
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

        final UUID projectId;
        try {
            ProjectAuthenticationContext project = projectSlugResolver.resolve(slug);
            projectId = project.projectId();
        } catch (RuntimeException unknownProject) {
            return;
        }

        Authentication principalAuth = context.getPrincipal();
        Object principal = (principalAuth == null) ? null : principalAuth.getPrincipal();
        if (!(principal instanceof ProjectUserPrincipal pup)) {
            // Flujo sin usuario final (client credentials, etc.): no añadimos claims de usuario.
            return;
        }

        context.getClaims().claim("project_id", projectId.toString());
        context.getClaims().claim("authz_version", pup.authzVersion());
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
