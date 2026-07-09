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
 * Redirección de verificación del Device Authorization Grant (RFC 8628). SAS redirige
 * aquí (al configurar {@code consentPage} del device-verification endpoint) cuando un
 * usuario abre el {@code verification_uri} para aprobar un dispositivo, añadiendo
 * {@code client_id}, {@code scope}, {@code state} y {@code user_code} como query params.
 *
 * <p>Igual que {@link ConsentController}, esta controller NO renderiza HTML: responde con
 * una 302 a la <b>página de verificación Next.js</b>, reenviando esos params más el
 * endpoint de verificación absoluto ({@code action} = {@code /p/{slug}/oauth2/device_verification})
 * y el token CSRF enmascarado. El form Next.js hace el POST nativo de vuelta a
 * {@code action} para completar la verificación.</p>
 *
 * <p><b>Multi-issuer:</b> el POST de verificación debe volver a
 * {@code /p/{slug}/oauth2/device_verification} (no al global) para que el issuer se
 * resuelva al realm del proyecto. Como SAS pierde el segmento {@code /p/{slug}} al
 * redirigir aquí, el slug se reconstruye desde el {@code client_id}.</p>
 */
@Controller
public class DeviceVerificationController {

    private static final String GLOBAL_DEVICE_VERIFICATION_ENDPOINT = "/oauth2/device_verification";

    private final ProjectOauthClientRepository projectOauthClientRepository;
    private final ProjectLookupService projectLookupService;
    private final String frontendBaseUrl;

    public DeviceVerificationController(
            ProjectOauthClientRepository projectOauthClientRepository,
            ProjectLookupService projectLookupService,
            @Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        this.projectOauthClientRepository = projectOauthClientRepository;
        this.projectLookupService = projectLookupService;
        this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/+$", "");
    }

    @GetMapping("/oauth2/device")
    public void device(
            @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
            @RequestParam(value = OAuth2ParameterNames.SCOPE, required = false) String scope,
            @RequestParam(OAuth2ParameterNames.STATE) String state,
            @RequestParam(OAuth2ParameterNames.USER_CODE) String userCode,
            CsrfToken csrfToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        Optional<ProjectOauthClient> client = projectOauthClientRepository.findByClientId(clientId);
        Optional<ProjectSlug> slug = client
                .map(c -> new ProjectSlug(projectLookupService.requireSlug(c.getProjectId())));
        String clientName = client.map(ProjectOauthClient::getName).orElse(null);

        String actionPath = slug
                .map(s -> "/p/" + s.value + GLOBAL_DEVICE_VERIFICATION_ENDPOINT)
                .orElse(GLOBAL_DEVICE_VERIFICATION_ENDPOINT);
        String absoluteAction = absoluteApiUrl(request, actionPath);
        String devicePath = slug
                .map(s -> "/p/" + s.value + "/oauth2/device")
                .orElse("/oauth2/device");

        StringBuilder target = new StringBuilder(frontendBaseUrl).append(devicePath)
                .append("?client_id=").append(enc(clientId))
                .append("&state=").append(enc(state))
                .append("&user_code=").append(enc(userCode));
        if (clientName != null && !clientName.isBlank()) {
            target.append("&client_name=").append(enc(clientName));
        }
        if (scope != null && !scope.isBlank()) {
            target.append("&scope=").append(enc(scope));
        }
        target.append("&action=").append(enc(absoluteAction));
        // Token CSRF (enmascarado o crudo según el handler de la cadena del AS). Se reenvía
        // al form Next.js, que lo reenvía tal cual en el POST de verificación → valida.
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
