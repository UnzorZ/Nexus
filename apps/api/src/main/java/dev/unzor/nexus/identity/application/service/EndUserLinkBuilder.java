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
 * registrado por el proyecto (ver {@link #verifyEmailLink(String, String)},
 * {@link #passwordResetLink(String, String)} y los enlaces de federación Google), de forma que
 * el alta, el reseteo y el login federado self-service se completan en el dominio de la propia
 * app.</p>
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
     * {@link #passwordResetLink(String, String)}). Los enlaces de federación Google
     * ({@link #googleLinkRequiredLink(String)}, {@link #googleErrorLink(String, String)} y el
     * landing {@link #appLandingLink(String)}) también aterrizan en el origen de la app; solo el
     * portal propio ({@link #portalLink(String)}) sigue en el frontend de Nexus como fallback
     * para proyectos sin origen registrado.</p>
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
     * Post-login landing for a federated (Google) login that has no authorization flow to
     * resume. Lands on the <b>app</b> origin — marked {@code ?from=google} — so the app can
     * resume its own OIDC authorize endpoint and mint its product session.
     */
    public String appLandingLink(String projectSlug) {
        return appOrigin(projectSlug, "/login?from=google");
    }

    /**
     * Page where the end-user re-authenticates (password) to confirm linking a Google identity
     * to an existing account. Lands on the <b>app</b> origin so the app hosts the linking page.
     */
    public String googleLinkRequiredLink(String projectSlug) {
        return appOrigin(projectSlug, "/login?google_link_required=1");
    }

    /**
     * App login page with a Google federation error code, so the SPA can show the reason (for
     * example a denied consent or an unverified email). Lands on the <b>app</b> origin.
     */
    public String googleErrorLink(String projectSlug, String code) {
        return appOrigin(projectSlug, "/login?google_error=" + urlEncode(code == null ? "error" : code));
    }

    /**
     * A path on the project's own app origin, falling back to the {@link #portalLink(String)}
     * portal when the project declares no origin. The whole Google flow (success, link-required,
     * error) lands on the app this way, so the app owns every step.
     */
    private String appOrigin(String projectSlug, String path) {
        return allowedOriginsResolver.primaryProjectOrigin(projectSlug)
                .map(EndUserLinkBuilder::stripTrailingSlash)
                .map(origin -> origin + path)
                .orElse(portalLink(projectSlug));
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
