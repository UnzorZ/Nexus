package dev.unzor.nexus.identity.application.observability;

import org.springframework.stereotype.Component;

/**
 * Estado del origen de claves de firma JWT: indica si las claves en uso son
 * efímeras (generadas en memoria) porque no se configuró un keystore.
 *
 * <p>Lo consulta {@link JwkSigningKeyHealthIndicator} para reflejar esa condición
 * en el health check de readiness: en producción/multi-instancia sin keystore la
 * readiness debe caer. {@code markEphemeral()} lo fija {@code SecurityConfig}
 * durante la construcción del {@code JWKSource} cuando toma el camino efímero.</p>
 */
@Component
public class NexusOAuthJwkState {

    private volatile boolean ephemeral;

    public boolean isEphemeral() {
        return ephemeral;
    }

    public void markEphemeral() {
        this.ephemeral = true;
    }
}
