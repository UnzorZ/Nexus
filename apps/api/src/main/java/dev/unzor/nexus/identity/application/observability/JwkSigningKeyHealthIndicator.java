package dev.unzor.nexus.identity.application.observability;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health check de las claves de firma JWT.
 *
 * <p>Reporta {@code DOWN} cuando las claves son efímeras (sin keystore
 * configurado), de modo que el grupo de readiness falle en despliegues de
 * producción/multi-instancia mal configurados. En desarrollo este estado DOWN es
 * esperado y no impide arrancar: simplemente señala que la instancia no está
 * lista para producción.</p>
 */
@Component
public class JwkSigningKeyHealthIndicator implements HealthIndicator {

    private final NexusOAuthJwkState state;

    public JwkSigningKeyHealthIndicator(NexusOAuthJwkState state) {
        this.state = state;
    }

    @Override
    public Health health() {
        if (state.isEphemeral()) {
            return Health.down()
                    .withDetail("keystoreConfigured", false)
                    .withDetail("reason",
                            "Ephemeral in-memory signing keys; configure nexus.oauth.jwk.keystore-location "
                                    + "for production or multi-instance deployments")
                    .build();
        }
        return Health.up().withDetail("keystoreConfigured", true).build();
    }
}
