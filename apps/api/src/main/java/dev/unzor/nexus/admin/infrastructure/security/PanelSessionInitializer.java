package dev.unzor.nexus.admin.infrastructure.security;

import dev.unzor.nexus.admin.application.configuration.PanelSessionConfiguration;
import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Inicializa los atributos de dominio de la sesión del panel tras un login JSON
 * correcto ({@code POST /api/panel/v1/session/login}).
 *
 * <p>Centraliza el borrado del hash de contraseña del principal (antes de que el
 * {@code SecurityContext} se persista en Redis) y la población de los atributos
 * indexables por cuenta, para evitar duplicar esta lógica entre el success
 * handler y el controlador.</p>
 */
@Component
public class PanelSessionInitializer {

    public void initialize(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null || !(authentication.getPrincipal() instanceof NexusAccountPrincipal principal)) {
            return;
        }
        principal.eraseCredentials();
        session.setAttribute(PanelSessionConfiguration.ACCOUNT_ID, principal.accountId().toString());
        session.setAttribute(NexusSessionAttributes.SESSION_PUBLIC_ID, UUID.randomUUID().toString());
        session.setAttribute(
                NexusSessionAttributes.USER_AGENT,
                NexusSessionAttributes.truncateUserAgent(request.getHeader("User-Agent"))
        );
    }
}

