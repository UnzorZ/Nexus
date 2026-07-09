package io.nexus.client;

import io.nexus.client.api.AuthorizationSnapshot;
import io.nexus.client.api.HeartbeatReceipt;
import io.nexus.client.api.InstanceToken;
import io.nexus.client.api.NotifyMessage;
import io.nexus.client.api.PermissionDeclaration;
import io.nexus.client.api.PermissionDeclarationReceipt;
import io.nexus.client.internal.HeartbeatClient;
import io.nexus.client.internal.NotifyClient;
import io.nexus.client.internal.PermissionClient;

import java.util.List;
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

    public NexusClient(HeartbeatClient heartbeatClient, PermissionClient permissionClient,
                       PermissionSnapshotCache snapshotCache, NotifyClient notifyClient) {
        this.heartbeatClient = heartbeatClient;
        this.permissionClient = permissionClient;
        this.snapshotCache = snapshotCache;
        this.notifyClient = notifyClient;
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
}
