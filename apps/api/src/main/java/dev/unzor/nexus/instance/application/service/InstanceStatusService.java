package dev.unzor.nexus.instance.application.service;

import dev.unzor.nexus.instance.api.dto.InstanceStatus;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Construye el status de sólo lectura de la configuración operativa de la
 * instancia leyendo las properties vía {@link Environment} (sin acoplar el módulo
 * a vault/identity/registry/notify). Las claves sensibles no se exponen: sólo su
 * estado.
 */
@Service
public class InstanceStatusService {

    /**
     * Sentinel del default de dev de {@code nexus.vault.master-key} (espejo del
     * constante de {@code NotifyCrypto}/{@code VaultCrypto}). Se duplica aquí para
     * no acoplar este módulo a notify/vault; el valor es estable y bien conocido.
     */
    private static final String VAULT_DEV_DEFAULT = "nexus-dev-vault-master-key-do-not-use-in-prod";

    private final Environment environment;

    public InstanceStatusService(Environment environment) {
        this.environment = environment;
    }

    public InstanceStatus current() {
        String frontendBaseUrl = environment.getProperty("nexus.frontend-base-url", "");
        String appName = environment.getProperty("spring.application.name", "Nexus");
        String version = environment.getProperty("nexus.build.version");

        return new InstanceStatus(
                new InstanceStatus.RegistrationInfo("open",
                        "Open registration. The first account created becomes the instance admin."),
                new InstanceStatus.SessionInfo(
                        environment.getProperty("nexus.session.timeout", "7d"),
                        environment.getProperty("nexus.session.cookie.same-site", "Lax"),
                        Boolean.parseBoolean(environment.getProperty("nexus.session.cookie.secure", "false")),
                        Boolean.parseBoolean(environment.getProperty("nexus.session.cookie.http-only", "true"))),
                vaultKeyStatus(),
                jwtKeystoreStatus(),
                frontendBaseUrl,
                environment.getProperty("nexus.allowed-dev-origins", ""),
                new InstanceStatus.HeartbeatDefaults(
                        environment.getProperty("nexus.registry.heartbeat.interval-seconds", Integer.class, 30),
                        environment.getProperty("nexus.registry.heartbeat.timeout-seconds", Integer.class, 90)),
                new InstanceStatus.InstanceInfo(appName, version, frontendBaseUrl));
    }

    private InstanceStatus.VaultKeyStatus vaultKeyStatus() {
        String masterKey = environment.getProperty("nexus.vault.master-key");
        boolean isDevDefault = masterKey == null || masterKey.isBlank() || VAULT_DEV_DEFAULT.equals(masterKey);
        return new InstanceStatus.VaultKeyStatus(isDevDefault ? "dev-default" : "configured");
    }

    private InstanceStatus.JwtKeystoreStatus jwtKeystoreStatus() {
        String keystoreLocation = environment.getProperty("nexus.oauth.jwk.keystore-location");
        boolean persistent = keystoreLocation != null && !keystoreLocation.isBlank();
        return new InstanceStatus.JwtKeystoreStatus(
                persistent ? "persistent" : "ephemeral",
                persistent ? keystoreLocation : null);
    }
}
