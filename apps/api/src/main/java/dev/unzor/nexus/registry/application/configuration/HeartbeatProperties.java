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
    private int timeoutSeconds = 90;
    /**
     * Dead-band antes de alertar una instancia caída (spec §13.1): una instancia sin
     * latido durante más de este umbral dispara la notificación offline. Distinto
     * del {@code timeoutSeconds} (que es el cap de OFFLINE en el display); por
     * defecto 5 minutos para no alertar por flapping.
     */
    private int offlineNotifySeconds = 300;

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getOfflineNotifySeconds() {
        return offlineNotifySeconds;
    }

    public void setOfflineNotifySeconds(int offlineNotifySeconds) {
        this.offlineNotifySeconds = offlineNotifySeconds;
    }
}
