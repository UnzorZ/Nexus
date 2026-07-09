package io.nexus.client;

import io.nexus.client.api.HeartbeatReceipt;
import io.nexus.client.internal.HeartbeatClient;
import io.nexus.client.internal.HeartbeatScheduler;
import io.nexus.client.internal.NexusHttpClient;
import io.nexus.client.internal.NotifyClient;
import io.nexus.client.internal.PermissionClient;
import io.nexus.client.internal.PermissionDeclarationSync;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Autoconfiguración de la <b>mitad de gestión</b> del starter: {@link NexusClient}
 * + heartbeat + snapshot cache + declaración de permisos + notify. Se activa cuando
 * se configura {@code nexus.url} + {@code nexus.api-key}. La mitad de seguridad
 * OAuth2/OIDC vive en {@code NexusSecurityAutoConfiguration}.
 */
@AutoConfiguration
@EnableConfigurationProperties(NexusProperties.class)
@ConditionalOnProperty(name = "nexus.url")
public class NexusAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NexusHttpClient nexusHttpClient(NexusProperties properties) {
        return new NexusHttpClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public HeartbeatClient heartbeatClient(NexusHttpClient http) {
        return new HeartbeatClient(http);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionClient permissionClient(NexusHttpClient http) {
        return new PermissionClient(http);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyClient notifyClient(NexusHttpClient http) {
        return new NotifyClient(http);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionSnapshotCache permissionSnapshotCache(PermissionClient permissionClient, NexusProperties properties) {
        return new PermissionSnapshotCache(permissionClient,
                properties.getPermissions().getSnapshotTtl(),
                properties.getPermissions().isFailClosed());
    }

    @Bean
    @ConditionalOnMissingBean
    public NexusClient nexusClient(HeartbeatClient heartbeatClient, PermissionClient permissionClient,
                                   PermissionSnapshotCache snapshotCache, NotifyClient notifyClient) {
        return new NexusClient(heartbeatClient, permissionClient, snapshotCache, notifyClient);
    }

    /** Handshake + latido periódico; sólo si {@code nexus.heartbeat.enabled=true} (default). */
    @Bean
    @ConditionalOnProperty(name = "nexus.heartbeat.enabled", havingValue = "true", matchIfMissing = true)
    public HeartbeatScheduler heartbeatScheduler(NexusClient nexusClient, HeartbeatClient heartbeatClient,
                                                 NexusHttpClient http, NexusProperties properties) {
        HeartbeatScheduler.NexusClientBridge bridge = new HeartbeatScheduler.NexusClientBridge() {
            @Override
            public void registerAndUseToken() {
                try {
                    var token = heartbeatClient.register();
                    if (token != null && token.token() != null) {
                        http.useInstanceToken(token.token());
                    }
                } catch (RuntimeException ignored) {
                    // El scheduler ya loguea; seguimos con la API key cruda.
                }
            }

            @Override
            public HeartbeatReceipt heartbeat(String instanceId, String appName, String appVersion, String status) {
                return nexusClient.heartbeat().beat(instanceId, appName, appVersion, status);
            }
        };
        return new HeartbeatScheduler(bridge, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "nexus.heartbeat.enabled", havingValue = "true", matchIfMissing = true)
    public org.springframework.boot.ApplicationRunner nexusHeartbeatStarter(HeartbeatScheduler scheduler) {
        return args -> scheduler.start();
    }

    /** Sincronización declarativa de permisos al arrancar. */
    @Bean
    @ConditionalOnMissingBean
    public PermissionDeclarationSync permissionDeclarationSync(PermissionClient permissionClient,
                                                              NexusProperties properties,
                                                              List<PermissionDeclarationProvider> providers) {
        return new PermissionDeclarationSync(permissionClient, properties, providers);
    }
}
