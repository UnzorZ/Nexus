package dev.unzor.nexus.admin.infrastructure.security;

import dev.unzor.nexus.admin.application.configuration.PanelSessionConfiguration;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;
import java.util.UUID;

public class PanelAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final PanelContinueUrlValidator continueUrlValidator;

    public PanelAuthenticationSuccessHandler(PanelContinueUrlValidator continueUrlValidator) {
        this.continueUrlValidator = continueUrlValidator;
        setDefaultTargetUrl(continueUrlValidator.defaultDashboardUrl());
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        populateSessionAttributes(request, authentication);
        erasePrincipalCredentials(authentication);

        String continueUrl = request.getParameter("continue");
        if (continueUrlValidator.isAllowed(continueUrl)) {
            getRedirectStrategy().sendRedirect(request, response, continueUrl);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * Persiste los atributos de dominio de la sesión del panel.
     *
     * <p>La sesión está respaldada por Redis mediante Spring Session. Spring Security
     * almacena el {@code SecurityContext} en la sesión por su cuenta; Nexus solo añade
     * aquí tres atributos simples y serializables con JDK: el identificador estable de
     * la cuenta (usado como índice por {@code RedisIndexedSessionRepository}), un
     * identificador público de gestión distinto del {@code JSESSIONID} interno y el
     * {@code User-Agent} truncado. Nunca se almacena la entidad JPA. El
     * {@code SecurityContext} persistido referencia al
     * {@link NexusAccountPrincipal}, cuyo hash de contraseña se borra en
     * {@link #erasePrincipalCredentials(Authentication)} antes de la serialización, de
     * modo que el principal almacenado en Redis no conserva el hash.</p>
     */
    private static void populateSessionAttributes(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null || !(authentication.getPrincipal() instanceof NexusAccountPrincipal principal)) {
            return;
        }

        session.setAttribute(PanelSessionConfiguration.ACCOUNT_ID, principal.accountId().toString());
        session.setAttribute(PanelSessionConfiguration.SESSION_PUBLIC_ID, UUID.randomUUID().toString());
        session.setAttribute(
                PanelSessionConfiguration.USER_AGENT,
                truncate(request.getHeader("User-Agent"), PanelSessionConfiguration.USER_AGENT_MAX_LENGTH)
        );
    }

    /**
     * Borra el hash de la contraseña del principal tras un login correcto, antes de que
     * el {@code SecurityContext} se persista en la sesión (y, por tanto, en Redis). El
     * principal implementa {@link org.springframework.security.core.CredentialsContainer}
     * y nunca retiene el hash una vez serializado.
     */
    private static void erasePrincipalCredentials(Authentication authentication) {
        if (authentication.getPrincipal() instanceof NexusAccountPrincipal principal) {
            principal.eraseCredentials();
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
