package dev.unzor.nexus.identity.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Construye los enlaces públicos que se envían por email a los usuarios finales de un
 * proyecto (verificación de email, reseteo de contraseña).
 *
 * <p>Estos enlaces apuntan al <b>frontend Next.js</b> (donde viven las páginas de usuario
 * final), no al API: el usuario aterriza en la página Next.js, que a su vez llama a la
 * API JSON correspondiente. El base URL por defecto se toma de {@code nexus.frontend-base-url}
 * (siempre configurado; por defecto {@code http://localhost:3000} en desarrollo). Los enlaces
 * de verificación de email y de reseteo de contraseña usan además el origen <b>de la app</b>
 * registrado por el proyecto (ver {@link #verifyEmailLink(String, String)} y
 * {@link #passwordResetLink(String, String)}), de forma que el alta y el reseteo self-service
 * se completan en el dominio de la propia app.</p>
 */
@Component
public class EndUserLinkBuilder {

    private final String frontendBaseUrl;
    private final ProjectAllowedOriginsResolver allowedOriginsResolver;

    public EndUserLinkBuilder(
            @Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
            ProjectAllowedOriginsResolver allowedOriginsResolver
    ) {
        this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        this.allowedOriginsResolver = allowedOriginsResolver;
    }

    /**
     * Enlace de verificación de email. Aterriza en el dominio de la <b>app</b> (el origen
     * que el proyecto registró como redirect URI), no en el frontend propio de Nexus, de
     * forma que el alta self-service se completa sin salir de la app. Si el proyecto no
     * expone ningún origen registrado, cae al {@code frontend-base-url} (portal hosteado
     * por Nexus).
     *
     * <p>El reseteo de contraseña sigue el mismo criterio (ver
     * {@link #passwordResetLink(String, String)}). Sólo el portal y los enlaces de federación
     * siguen en el frontend propio de Nexus, porque esas páginas aún no las hostea la app.</p>
     */
    public String verifyEmailLink(String projectSlug, String rawToken) {
        return appBaseForSlug(projectSlug) + "/p/" + projectSlug + "/verify-email?token=" + urlEncode(rawToken);
    }

    /**
     * Base del dominio de la app para un proyecto, o el frontend propio de Nexus (fallback
     * seguro) si el proyecto no declara orígenes.
     */
    private String appBaseForSlug(String projectSlug) {
        return allowedOriginsResolver.primaryProjectOrigin(projectSlug)
                .map(EndUserLinkBuilder::stripTrailingSlash)
                .orElse(base());
    }

    /**
     * Enlace de confirmación de reseteo de contraseña. Aterriza en el dominio de la <b>app</b>
     * (mismo criterio que {@link #verifyEmailLink(String, String)}): la app hostea la página de
     * confirmación, de forma que el reseteo self-service se completa sin salir de la app. Si el
     * proyecto no declara orígenes, cae al {@code frontend-base-url}.
     */
    public String passwordResetLink(String projectSlug, String rawToken) {
        return appBaseForSlug(projectSlug) + "/p/" + projectSlug + "/password-reset/confirm?token=" + urlEncode(rawToken);
    }

    /**
     * Default landing page of a project's end-user portal (used after a federated login that
     * has no authorization flow to resume).
     */
    public String portalLink(String projectSlug) {
        return base() + "/p/" + projectSlug;
    }

    /**
     * Page where the end-user re-authenticates (password) to confirm linking a Google
     * identity to an existing account.
     */
    public String googleLinkRequiredLink(String projectSlug) {
        return base() + "/p/" + projectSlug + "/login?google_link_required=1";
    }

    /**
     * End-user login page with a Google federation error code, so the SPA can show the reason
     * (for example a denied consent or an unverified email).
     */
    public String googleErrorLink(String projectSlug, String code) {
        return base() + "/p/" + projectSlug + "/login?google_error=" + urlEncode(code == null ? "error" : code);
    }

    private String base() {
        return stripTrailingSlash(frontendBaseUrl);
    }

    private static String stripTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
