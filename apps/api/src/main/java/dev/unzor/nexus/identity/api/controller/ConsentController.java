package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Redirección de consentimiento con branding del AS multi-issuer (B3). SAS redirige aquí
 * (al configurar {@code consentPage}) cuando un cliente requiere consentimiento
 * ({@code consent_required}), añadiendo {@code client_id}, {@code scope} y {@code state}
 * como query params. Esta controller NO renderiza HTML (Thymeleaf eliminado): responde con
 * una 302 a la <b>página de consentimiento Next.js</b>, reenviando esos params más el
 * endpoint de autorización absoluto ({@code action}) y el token CSRF enmascarado.
 *
 * <p><b>Multi-issuer:</b> el POST de consent debe volver a {@code /p/{slug}/oauth2/authorize}
 * (no al global {@code /oauth2/authorize}) para que el issuer se resuelva al realm del
 * proyecto correcto. La redirección de SAS a esta página pierde el segmento
 * {@code /p/{slug}}, así que el slug se reconstruye aquí desde el {@code client_id}. Para el
 * cliente técnico global (bootstrap) se usa el endpoint global.</p>
 *
 * <p>El contrato del POST de consent (ver {@code OAuth2AuthorizationConsentAuthenticationConverter})
 * exige únicamente {@code client_id} + {@code state} + los {@code scope} concedidos; el
 * endpoint de autorización distingue el envío de consentimiento de una petición de
 * autorización por la ausencia de {@code response_type}/{@code redirect_uri}.</p>
 *
 * <p><b>CSRF:</b> se reenvía {@code csrfToken.getToken()} (el valor enmascarado por el
 * {@code XorCsrfTokenRequestAttributeHandler} cuando aplica, o el crudo si el handler es
 * plain). Al reenviarlo tal cual en el POST nativo del formulario, el {@code CsrfFilter}
 * lo resuelve al token crudo del repositorio y valida (demostrado en el código fuente de
 * Spring Security: {@code CsrfFilter} compara el token crudo del repo contra
 * {@code resolveCsrfTokenValue}). Así la cadena del AS queda intacta.</p>
 */
@Controller
public class ConsentController {

    private static final String GLOBAL_AUTHORIZE_ENDPOINT = "/oauth2/authorize";

    private final ProjectOauthClientRepository projectOauthClientRepository;
    private final ProjectLookupService projectLookupService;
    private final String frontendBaseUrl;

    public ConsentController(
            ProjectOauthClientRepository projectOauthClientRepository,
            ProjectLookupService projectLookupService,
            @Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        this.projectOauthClientRepository = projectOauthClientRepository;
        this.projectLookupService = projectLookupService;
        this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/+$", "");
    }

    @GetMapping("/oauth2/consent")
    public void consent(
            @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
            @RequestParam(value = OAuth2ParameterNames.SCOPE, required = false) String scope,
            @RequestParam(OAuth2ParameterNames.STATE) String state,
            CsrfToken csrfToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        // Una sola consulta resuelve el slug del proyecto (SAS pierde /p/{slug} al
        // redirigir aquí) y el nombre legible del cliente, que la página muestra en
        // lugar del client_id crudo. El cliente técnico global (bootstrap) no es un
        // ProjectOauthClient → optional vacío → endpoint global, sin nombre.
        Optional<ProjectOauthClient> client = projectOauthClientRepository.findByClientId(clientId);
        Optional<ProjectSlug> slug = client
                .map(c -> new ProjectSlug(projectLookupService.requireSlug(c.getProjectId())));
        String clientName = client.map(ProjectOauthClient::getName).orElse(null);
        String actionPath = slug
                .map(s -> "/p/" + s.value + GLOBAL_AUTHORIZE_ENDPOINT)
                .orElse(GLOBAL_AUTHORIZE_ENDPOINT);
        String absoluteAction = absoluteApiUrl(request, actionPath);
        String consentPath = slug
                .map(s -> "/p/" + s.value + "/oauth2/consent")
                .orElse("/oauth2/consent");

        StringBuilder target = new StringBuilder(frontendBaseUrl).append(consentPath)
                .append("?client_id=").append(enc(clientId))
                .append("&state=").append(enc(state));
        if (clientName != null && !clientName.isBlank()) {
            target.append("&client_name=").append(enc(clientName));
        }
        if (scope != null && !scope.isBlank()) {
            target.append("&scope=").append(enc(scope));
        }
        target.append("&action=").append(enc(absoluteAction));
        // Token CSRF (enmascarado o crudo según el handler de la cadena del AS). Se reenvía
        // al form Next.js, que lo reenvía tal cual en el POST de consent → valida.
        target.append("&_csrf=").append(enc(csrfToken.getToken()));
        response.sendRedirect(target.toString());
    }

    private static String absoluteApiUrl(HttpServletRequest request, String path) {
        StringBuilder b = new StringBuilder()
                .append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        if (port > 0 && !isDefaultPort(request.getScheme(), port)) {
            b.append(':').append(port);
        }
        b.append(path);
        return b.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private record ProjectSlug(String value) {
    }
}
