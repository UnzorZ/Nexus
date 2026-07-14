package dev.unzor.nexus.sdk;

import dev.unzor.nexus.sdk.api.AuthorizationSnapshot;
import dev.unzor.nexus.sdk.api.ConfigValue;
import dev.unzor.nexus.sdk.api.HeartbeatReceipt;
import dev.unzor.nexus.sdk.api.InstanceToken;
import dev.unzor.nexus.sdk.api.MetricPoint;
import dev.unzor.nexus.sdk.api.NotifyMessage;
import dev.unzor.nexus.sdk.api.PermissionDeclaration;
import dev.unzor.nexus.sdk.api.PermissionDeclarationReceipt;
import dev.unzor.nexus.sdk.api.VaultSecret;
import dev.unzor.nexus.sdk.api.VaultSecretSummary;
import dev.unzor.nexus.sdk.internal.ConfigClient;
import dev.unzor.nexus.sdk.internal.HeartbeatClient;
import dev.unzor.nexus.sdk.internal.MetricsClient;
import dev.unzor.nexus.sdk.internal.NotifyClient;
import dev.unzor.nexus.sdk.internal.PermissionClient;
import dev.unzor.nexus.sdk.internal.VaultClient;

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
