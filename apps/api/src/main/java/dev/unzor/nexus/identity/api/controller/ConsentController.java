package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;

/**
 * Pantalla de consentimiento con branding del AS multi-issuer (B3). SAS redirige
 * aquí (al configurar {@code consentPage}) cuando un cliente requiere consentimiento
 * ({@code consent_required}), añadiendo {@code client_id}, {@code scope} y
 * {@code state} como query params. Renderiza un formulario que reenvía los scopes
 * concedidos al endpoint de autorización.
 *
 * <p><b>Multi-issuer:</b> el POST debe volver a {@code /p/{slug}/oauth2/authorize}
 * (no al global {@code /oauth2/authorize}) para que el issuer se resuelva al realm
 * del proyecto correcto. La redirección de SAS a esta página (resuelta desde
 * {@code consentPage} + el contexto de la petición) pierde el segmento {@code /p/{slug}},
 * así que el slug se reconstruye aquí desde el {@code client_id}. Para el cliente
 * técnico global (bootstrap) se usa el endpoint global.</p>
 *
 * <p>El contrato del POST de consent (ver {@code OAuth2AuthorizationConsentAuthenticationConverter})
 * exige únicamente {@code client_id} + {@code state} + los {@code scope} concedidos;
 * el endpoint de autorización distingue el envío de consentimiento de una petición de
 * autorización por la ausencia de {@code response_type}/{@code redirect_uri}.</p>
 */
@Controller
public class ConsentController {

    private static final String GLOBAL_AUTHORIZE_ENDPOINT = "/oauth2/authorize";

    private final ProjectOauthClientRepository projectOauthClientRepository;
    private final ProjectLookupService projectLookupService;

    public ConsentController(
            ProjectOauthClientRepository projectOauthClientRepository,
            ProjectLookupService projectLookupService
    ) {
        this.projectOauthClientRepository = projectOauthClientRepository;
        this.projectLookupService = projectLookupService;
    }

    @GetMapping("/oauth2/consent")
    public String consent(
            @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
            @RequestParam(value = OAuth2ParameterNames.SCOPE, required = false) String scope,
            @RequestParam(OAuth2ParameterNames.STATE) String state,
            CsrfToken csrfToken,
            Model model
    ) {
        List<String> scopes = parseScopes(scope);
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", clientDisplayName(clientId));
        model.addAttribute("state", state);
        model.addAttribute("scopes", scopes);
        model.addAttribute("authorizeAction", resolveAuthorizeAction(clientId));
        model.addAttribute("csrf", csrfToken);
        return "identity/project-consent";
    }

    private String clientDisplayName(String clientId) {
        return projectOauthClientRepository.findByClientId(clientId)
                .map(c -> c.getName())
                .orElse(clientId);
    }

    private String resolveAuthorizeAction(String clientId) {
        return projectOauthClientRepository.findByClientId(clientId)
                .map(c -> "/p/" + projectLookupService.requireSlug(c.getProjectId()) + GLOBAL_AUTHORIZE_ENDPOINT)
                .orElse(GLOBAL_AUTHORIZE_ENDPOINT);
    }

    private static List<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scope.split(" "))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
