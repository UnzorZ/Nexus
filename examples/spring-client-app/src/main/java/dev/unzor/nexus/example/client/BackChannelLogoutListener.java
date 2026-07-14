package dev.unzor.nexus.example.client;

import dev.unzor.nexus.sdk.security.NexusBackChannelLogoutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Recibe los logout tokens back-channel válidos (RFC 8417) que Nexus envía cuando
 * la sesión de un usuario termina. En una app real, aquí invalidarías la sesión
 * local del {@code sub} (p. ej. vía Spring Session). Aquí lo logueamos como demo.
 */
@Component
public class BackChannelLogoutListener {

    private static final Logger log = LoggerFactory.getLogger(BackChannelLogoutListener.class);

    @EventListener
    public void onBackChannelLogout(NexusBackChannelLogoutEvent event) {
        log.info("Back-channel logout recibido para sub={} (iss={}) — invalidar sesión local",
                event.sub(), event.iss());
    }
}
