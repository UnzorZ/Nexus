package dev.unzor.nexus.registry.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Umbrales de liveness de heartbeat (spec §13.1). {@code intervalSeconds} = cada
 * cuánto se espera un latido (banda ONLINE); {@code timeoutSeconds} = a partir de
 * cuándo una instancia se asume OFFLINE. Entre ambos queda la banda STALE. Son
 * valores globales (no por proyecto) y configurables vía
 * {@code nexus.registry.heartbeat.*}.
 */
@ConfigurationProperties(prefix = "nexus.registry.heartbeat")
public class HeartbeatProperties {

    private int intervalSeconds = 30;
    private int staleAfterSeconds = 60;
    private int timeoutSeconds = 90;

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public int getStaleAfterSeconds() {
        return staleAfterSeconds;
    }

    public void setStaleAfterSeconds(int staleAfterSeconds) {
        this.staleAfterSeconds = staleAfterSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
