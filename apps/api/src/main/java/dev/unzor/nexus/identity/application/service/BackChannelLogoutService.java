package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Fan-out del back-channel logout (OIDC RFC 8417): al terminar la sesión de un usuario final,
 * resuelve los clientes del realm con sesión para ese usuario, emite un logout token firmado
 * por cada uno y se lo POSTea (form-urlencoded {@code logout_token=<jwt>}) a su
 * {@code backchannel_logout_uri}.
 *
 * <p>Asíncrono (no bloquea el logout del usuario) vía el executor de notificaciones. La entrega
 * se reintenta con backoff exponencial sobre fallos transitorios (red caída, 5xx del RP); un 4xx
 * es un rechazo definitivo del RP y no se reintenta (RFC 8417 §2.7). Los fallos definitivos de
 * un cliente se loguean y no impiden los demás; el RP recuperará la consistencia en su próximo
 * login (re-emisión).</p>
 */
@Component
public class BackChannelLogoutService {

    private static final Logger log = LoggerFactory.getLogger(BackChannelLogoutService.class);

    /**
     * Reintentos de entrega del logout token (RFC 8417 §2.7: el OP SHOULD reintentar).
     * Cuenta total de intentos por cliente (incluido el primero). Acotado para no retener
     * hilos del {@code notifyExecutor}; el RP recupera consistencia en su próximo login.
     */
    static final int MAX_ATTEMPTS = 3;
    /** Backoff inicial entre reintentos; dobla en cada intento (exponencial). */
    static final long INITIAL_BACKOFF_MS = 500L;

    private final BackChannelLogoutClientResolver resolver;
    private final BackChannelLogoutTokenIssuer tokenIssuer;
    private final RestClient httpClient;

    public BackChannelLogoutService(
            BackChannelLogoutClientResolver resolver,
            BackChannelLogoutTokenIssuer tokenIssuer,
            RestClient httpClient
    ) {
        this.resolver = resolver;
        this.tokenIssuer = tokenIssuer;
        this.httpClient = httpClient;
    }

    @Async("notifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLogoutRequested(BackChannelLogoutRequested event) {
        List<BackChannelLogoutTarget> targets = event.targets().isEmpty()
                ? resolver.resolve(event.principalName(), event.projectId()).stream()
                        .map(BackChannelLogoutService::snapshot)
                        .toList()
                : event.targets();
        if (targets.isEmpty()) {
            return;
        }
        for (BackChannelLogoutTarget target : targets) {
            send(target, event);
        }
    }

    private static BackChannelLogoutTarget snapshot(ProjectOauthClient client) {
        return new BackChannelLogoutTarget(
                client.getId(), client.getClientId(), client.getBackchannelLogoutUri());
    }

    private void send(BackChannelLogoutTarget client, BackChannelLogoutRequested event) {
        String logoutToken;
        try {
            logoutToken = tokenIssuer.issue(event.issuer(), event.principalName()).getTokenValue();
        } catch (Exception e) {
            log.warn("Back-channel logout: no se pudo emitir el token para el cliente {}: {}",
                    client.clientId(), e.getMessage());
            return;
        }
        // RFC 8417 §2.7: el OP SHOULD reintentar la entrega. Reintentamos con backoff
        // exponencial sobre fallos transitorios (red caída, 5xx del RP). Un 4xx es un
        // rechazo definitivo del RP (token inválido para él) — no se reintenta.
        String body = "logout_token=" + URLEncoder.encode(logoutToken, StandardCharsets.UTF_8);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                httpClient.post()
                        .uri(client.logoutUri())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Back-channel logout enviado al cliente {} para el usuario {} (intento {}/{})",
                        client.clientId(), event.principalName(), attempt, MAX_ATTEMPTS);
                return;
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().is4xxClientError()) {
                    log.warn("Back-channel logout rechazado (4xx) por el cliente {} ({}): {} — no se reintenta",
                            client.clientId(), client.logoutUri(), e.getMessage());
                    return;
                }
                if (!retry(client, attempt, e)) {
                    return;
                }
            } catch (RestClientException e) {
                if (!retry(client, attempt, e)) {
                    return;
                }
            }
        }
    }

    /**
     * Duerme el backoff exponencial y devuelve {@code true} si quedan intentos, {@code false}
     * si era el último (entrega fallida definitiva — el RP recuperará consistencia al re-emitir
     * en el próximo login).
     */
    private boolean retry(BackChannelLogoutTarget client, int attempt, Exception cause) {
        if (attempt >= MAX_ATTEMPTS) {
            log.warn("Back-channel logout FALLÓ para el cliente {} ({}) tras {} intentos: {}",
                    client.clientId(), client.logoutUri(), MAX_ATTEMPTS, cause.getMessage());
            return false;
        }
        long backoff = INITIAL_BACKOFF_MS << (attempt - 1);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }
}
