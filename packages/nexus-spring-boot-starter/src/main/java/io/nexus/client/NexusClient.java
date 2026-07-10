package io.nexus.client;

import io.nexus.client.api.AuthorizationSnapshot;
import io.nexus.client.api.ConfigValue;
import io.nexus.client.api.HeartbeatReceipt;
import io.nexus.client.api.InstanceToken;
import io.nexus.client.api.MetricPoint;
import io.nexus.client.api.NotifyMessage;
import io.nexus.client.api.PermissionDeclaration;
import io.nexus.client.api.PermissionDeclarationReceipt;
import io.nexus.client.api.VaultSecret;
import io.nexus.client.api.VaultSecretSummary;
import io.nexus.client.internal.ConfigClient;
import io.nexus.client.internal.HeartbeatClient;
import io.nexus.client.internal.MetricsClient;
import io.nexus.client.internal.NotifyClient;
import io.nexus.client.internal.PermissionClient;
import io.nexus.client.internal.VaultClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fachada del SDK de Nexus (spec §18). Expone los sub-clientes tipados:
 * <pre>
 *   nexus.permissions().can(userId, "orders.cancel");
 *   nexus.permissions().snapshot(userId);
 *   nexus.notify().send(NotifyMessage.plain(to, subject, body));
 *   nexus.heartbeat().heartbeat(instanceId, appName, version, "up");
 * </pre>
 * Es el bean principal que las apps cliente inyectan.
 */
public class NexusClient {

    private final HeartbeatClient heartbeatClient;
    private final PermissionClient permissionClient;
    private final PermissionSnapshotCache snapshotCache;
    private final NotifyClient notifyClient;
    private final ConfigClient configClient;
    private final VaultClient vaultClient;
    private final MetricsClient metricsClient;

    public NexusClient(HeartbeatClient heartbeatClient, PermissionClient permissionClient,
                       PermissionSnapshotCache snapshotCache, NotifyClient notifyClient,
                       ConfigClient configClient, VaultClient vaultClient, MetricsClient metricsClient) {
        this.heartbeatClient = heartbeatClient;
        this.permissionClient = permissionClient;
        this.snapshotCache = snapshotCache;
        this.notifyClient = notifyClient;
        this.configClient = configClient;
        this.vaultClient = vaultClient;
        this.metricsClient = metricsClient;
    }

    /** Latido + handshake de instancia. */
    public Heartbeat heartbeat() {
        return new Heartbeat();
    }

    /** Permisos: snapshot cacheado + resolución local de comodines. */
    public Permissions permissions() {
        return new Permissions();
    }

    /** Notificaciones (scope {@code notify:send}). Renombrado de notify() — choca con Object.notify(). */
    public Notify notifications() {
        return new Notify();
    }

    /** Configuración del proyecto (scope {@code config:read}). */
    public Config config() {
        return new Config();
    }

    /** Vault del proyecto (scope {@code vault:read}). */
    public Vault vault() {
        return new Vault();
    }

    /** Métricas del proyecto — reporte push (scope {@code metrics:write}). */
    public Metrics metrics() {
        return new Metrics();
    }

    public class Heartbeat {
        public InstanceToken register() { return heartbeatClient.register(); }
        public HeartbeatReceipt beat(String instanceId, String appName, String appVersion, String status) {
            return heartbeatClient.heartbeat(instanceId, appName, appVersion, status);
        }
    }

    public class Permissions {
        public boolean can(UUID userId, String key) { return snapshotCache.can(userId, key); }
        public AuthorizationSnapshot snapshot(UUID userId) { return snapshotCache.get(userId); }
        public List<String> effective(UUID userId) { return snapshotCache.permissions(userId); }
        public PermissionDeclarationReceipt declare(List<PermissionDeclaration> declarations) {
            return permissionClient.declare(declarations);
        }
        public void invalidate(UUID userId) { snapshotCache.invalidate(userId); }
    }

    public class Notify {
        public void send(NotifyMessage message) { notifyClient.send(message); }
    }

    public class Config {
        public List<ConfigValue> list() { return configClient.list(); }
        public ConfigValue get(String key) { return configClient.get(key); }
    }

    public class Vault {
        public List<VaultSecretSummary> list() { return vaultClient.list(); }
        /** Revela el valor desencriptado del secreto (auditable en el backend). */
        public VaultSecret get(String key) { return vaultClient.get(key); }
    }

    public class Metrics {
        public MetricPoint record(String name, double value, Map<String, String> tags) {
            return metricsClient.record(name, value, tags);
        }
        public MetricPoint record(String name, double value) {
            return metricsClient.record(name, value);
        }
    }
}
