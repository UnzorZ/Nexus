package dev.unzor.nexus.instance.api.dto;

/**
 * Vista de sólo lectura de la configuración operativa de la instancia para el
 * panel del operador. Las claves sensibles (DB/Redis, master-key, keystore
 * password) son sólo-env y no se exponen: aquí se muestra su <em>estado</em>
 * (configurada vs dev-default, persistente vs efímera), no su valor.
 *
 * @param registration    política de registro (abierta; primer alta = admin, ADR-0010).
 * @param session         timeout y atributos efectivos de la cookie de sesión.
 * @param vaultMasterKey  estado de la master-key del vault (configured/dev-default).
 * @param jwtKeystore     estado del keystore de firma JWT (persistent/ephemeral).
 * @param frontendBaseUrl base URL pública del panel.
 * @param allowedOrigins  orígenes permitidos para CORS.
 * @param heartbeat       defaults globales del heartbeat del registry (env).
 * @param instance        metadatos de la instancia (nombre/versión/base URL).
 */
public record InstanceStatus(
        RegistrationInfo registration,
        SessionInfo session,
        VaultKeyStatus vaultMasterKey,
        JwtKeystoreStatus jwtKeystore,
        String frontendBaseUrl,
        String allowedOrigins,
        HeartbeatDefaults heartbeat,
        InstanceInfo instance
) {

    public record RegistrationInfo(String policy, String note) {
    }

    public record SessionInfo(String timeout, String cookieSameSite, boolean cookieSecure, boolean cookieHttpOnly) {
    }

    /** {@code configured} (master-key propia) o {@code dev-default} (insegura para prod). */
    public record VaultKeyStatus(String status) {
    }

    /** {@code persistent} (keystore propio) o {@code ephemeral} (sin keystore). */
    public record JwtKeystoreStatus(String status, String keystoreLocation) {
    }

    public record HeartbeatDefaults(int intervalSeconds, int timeoutSeconds) {
    }

    public record InstanceInfo(String appName, String version, String frontendBaseUrl) {
    }
}
