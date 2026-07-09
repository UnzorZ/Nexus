package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Fan-out del back-channel logout (OIDC RFC 8417): al terminar la sesión de un usuario final,
 * resuelve los clientes del realm con sesión para ese usuario, emite un logout token firmado
 * por cada uno y se lo POSTea (form-urlencoded {@code logout_token=<jwt>}) a su
 * {@code backchannel_logout_uri}.
 *
 * <p>Asíncrono (no bloquea el logout del usuario) vía el executor de notificaciones. Los
 * fallos de un cliente (RP caído, 4xx/5xx) se loguean y no impiden los demás; el RP recuperará
 * la consistencia en su próximo login (re-emisión). El reintento con backoff de la spec queda
 * fuera del MVP.</p>
 */
@Component
public class BackChannelLogoutService {

    private static final Logger log = LoggerFactory.getLogger(BackChannelLogoutService.class);

    private final BackChannelLogoutClientResolver resolver;
    private final BackChannelLogoutTokenIssuer tokenIssuer;
    private final RestClient httpClient;

    public BackChannelLogoutService(
            BackChannelLogoutClientResolver resolver,
            BackChannelLogoutTokenIssuer tokenIssuer
    ) {
        this.resolver = resolver;
        this.tokenIssuer = tokenIssuer;
        this.httpClient = RestClient.builder().build();
    }

    @Async("notifyExecutor")
    @EventListener
    public void onLogoutRequested(BackChannelLogoutRequested event) {
        List<ProjectOauthClient> clients = resolver.resolve(event.principalName(), event.projectId());
        if (clients.isEmpty()) {
            return;
        }
        for (ProjectOauthClient client : clients) {
            send(client, event);
        }
    }

    private void send(ProjectOauthClient client, BackChannelLogoutRequested event) {
        String logoutToken;
        try {
            logoutToken = tokenIssuer.issue(event.issuer(), event.principalName()).getTokenValue();
        } catch (Exception e) {
            log.warn("Back-channel logout: no se pudo emitir el token para el cliente {}: {}",
                    client.getClientId(), e.getMessage());
            return;
        }
        try {
            httpClient.post()
                    .uri(client.getBackchannelLogoutUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("logout_token=" + URLEncoder.encode(logoutToken, StandardCharsets.UTF_8))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Back-channel logout enviado al cliente {} para el usuario {}",
                    client.getClientId(), event.principalName());
        } catch (RestClientException e) {
            // RP caído o rechazó: no rompemos el logout del usuario ni los demás clientes.
            log.warn("Back-channel logout FALLÓ para el cliente {} ({}): {}",
                    client.getClientId(), client.getBackchannelLogoutUri(), e.getMessage());
        }
    }
}
