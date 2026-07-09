package io.nexus.client.internal;

import io.nexus.client.NexusProperties;
import io.nexus.client.api.HeartbeatReceipt;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Arranca el latido de la instancia hacia Nexus (spec §13.1): hace el handshake
 * ({@code /register}) una vez y repite el latido cada {@code nexus.heartbeat.interval}
 * con un {@link ScheduledExecutorService} propio (no depende de
 * {@code @EnableScheduling}). Los fallos se loguean y no tiran la app.
 */
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final NexusClientBridge client;
    private final NexusProperties properties;
    private final String instanceId;
    private final Duration interval;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nexus-heartbeat");
                t.setDaemon(true);
                return t;
            });
    private final AtomicBoolean started = new AtomicBoolean(false);

    public HeartbeatScheduler(NexusClientBridge client, NexusProperties properties) {
        this.client = client;
        this.properties = properties;
        this.instanceId = resolveInstanceId(properties);
        this.interval = properties.getHeartbeat().getInterval();
    }

    /** Arranca el handshake + latido periódico. Idempotente. */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            client.registerAndUseToken();
        } catch (RuntimeException e) {
            log.warn("Heartbeat register failed (los latidos usarán la API key cruda): {}", e.getMessage());
        }
        beat();
        executor.scheduleAtFixedRate(this::beat, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void beat() {
        try {
            String appName = properties.getAppName() == null ? "nexus-app" : properties.getAppName();
            HeartbeatReceipt receipt = client.heartbeat(instanceId, appName, null, "up");
            log.debug("Heartbeat OK para {} (próximo en {}s)", instanceId, receipt.nextHeartbeatInSeconds());
        } catch (RuntimeException e) {
            log.warn("Heartbeat FALLÓ para {}: {}", instanceId, e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

    public String instanceId() {
        return instanceId;
    }

    private static String resolveInstanceId(NexusProperties properties) {
        if (properties.getInstanceId() != null && !properties.getInstanceId().isBlank()) {
            return properties.getInstanceId();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "nexus-instance";
        }
    }

    /**
     * Puente al cliente de latido + handshake, para que el scheduler no dependa
     * de la fachada {@code NexusClient} completa.
     */
    public interface NexusClientBridge {
        void registerAndUseToken();
        HeartbeatReceipt heartbeat(String instanceId, String appName, String appVersion, String status);
    }
}
