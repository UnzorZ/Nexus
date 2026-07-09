package io.nexus.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuración del starter de Nexus ({@code nexus.*}). Cubre las dos mitades:
 * <ul>
 *   <li><b>Gestión</b>: {@link #getUrl() url} + {@link #getApiKey() api-key} +
 *       {@link #getHeartbeat() heartbeat} + {@link #getPermissions() permissions}
 *       (declaraciones + snapshot cache).</li>
 *   <li><b>Seguridad</b>: {@link #getSecurity() security} (issuer OIDC, credenciales
 *       del cliente, modo del resource server, logout back-channel).</li>
 * </ul>
 *
 * <pre>
 * nexus:
 *   url: https://nexus.example.com
 *   api-key: ${NEXUS_API_KEY}
 *   app-name: F-Shop API
 *   instance-id: fshop-api-main-01
 *   heartbeat: { enabled: true, interval: 30s }
 *   permissions:
 *     snapshot-ttl: 30s
 *     fail-closed: true
 *     declarations: [ { key: orders.read, label: Ver pedidos } ]
 *   security:
 *     issuer: https://nexus.example.com/p/f-shop
 *     rs-mode: jwt
 *     client: { client-id: ..., client-secret: ... }
 * </pre>
 */
@ConfigurationProperties("nexus")
public class NexusProperties {

    /** URL base del API de Nexus (sin {@code /p/{slug}}): p. ej. {@code https://nexus.example.com}. */
    private String url;

    /** API key larga ({@code X-Nexus-Api-Key}) con los scopes necesarios. */
    private String apiKey;

    /** Nombre de la aplicación (se envía en el heartbeat). */
    private String appName;

    /**
     * Identificador estable de la instancia dentro del proyecto (heartbeat). Si se
     * deja vacío, el starter usa el hostname de la máquina.
     */
    private String instanceId;

    private Heartbeat heartbeat = new Heartbeat();

    private Permissions permissions = new Permissions();

    private Security security = new Security();

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public Heartbeat getHeartbeat() { return heartbeat; }
    public void setHeartbeat(Heartbeat heartbeat) { this.heartbeat = heartbeat; }

    public Permissions getPermissions() { return permissions; }
    public void setPermissions(Permissions permissions) { this.permissions = permissions; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    /** Latido de la instancia hacia Nexus (spec §13.1). */
    public static class Heartbeat {
        /** Si {@code false}, no se arranca el scheduler de latido. */
        private boolean enabled = true;
        /** Intervalo entre latidos. */
        private Duration interval = Duration.ofSeconds(30);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
    }

    /** Caché de snapshots de permisos + declaración declarativa (spec §14.11, §18). */
    public static class Permissions {
        /** TTL del snapshot cacheado por usuario. */
        private Duration snapshotTtl = Duration.ofSeconds(30);
        /** Si {@code true}, deniega cuando el snapshot caduca y Nexus no responde. */
        private boolean failClosed = true;
        /** Permisos declarados desde YAML (se sincronizan al arrancar). */
        private List<Declaration> declarations = new ArrayList<>();

        public Duration getSnapshotTtl() { return snapshotTtl; }
        public void setSnapshotTtl(Duration snapshotTtl) { this.snapshotTtl = snapshotTtl; }
        public boolean isFailClosed() { return failClosed; }
        public void setFailClosed(boolean failClosed) { this.failClosed = failClosed; }
        public List<Declaration> getDeclarations() { return declarations; }
        public void setDeclarations(List<Declaration> declarations) { this.declarations = declarations; }

        public static class Declaration {
            private String key;
            private String label;

            public String getKey() { return key; }
            public void setKey(String key) { this.key = key; }
            public String getLabel() { return label; }
            public void setLabel(String label) { this.label = label; }
        }
    }

    /** Autoconfiguración de seguridad OAuth2/OIDC. */
    public static class Security {
        /** Issuer del realm del proyecto: {@code {origin}/p/{slug}}. */
        private String issuer;
        /** Modo del resource server: {@code jwt} (local) o {@code introspect}. */
        private String rsMode = "jwt";
        /** Credenciales del cliente OAuth. */
        private Client client = new Client();
        /** Ruta del endpoint de back-channel logout que el starter expone. */
        private String backchannelLogoutPath = "/logout/backchannel";
        /** Rutas protegidas por el resource server (por defecto {@code /api/**}). */
        private List<String> apiPaths = List.of("/api/**");
        /** Rutas públicas de la cadena cliente (login, home, estáticos). */
        private List<String> publicPaths = new ArrayList<>(List.of("/", "/login/**", "/error", "/css/**", "/webjars/**"));

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getRsMode() { return rsMode; }
        public void setRsMode(String rsMode) { this.rsMode = rsMode; }
        public Client getClient() { return client; }
        public void setClient(Client client) { this.client = client; }
        public String getBackchannelLogoutPath() { return backchannelLogoutPath; }
        public void setBackchannelLogoutPath(String backchannelLogoutPath) { this.backchannelLogoutPath = backchannelLogoutPath; }
        public List<String> getApiPaths() { return apiPaths; }
        public void setApiPaths(List<String> apiPaths) { this.apiPaths = apiPaths; }
        public List<String> getPublicPaths() { return publicPaths; }
        public void setPublicPaths(List<String> publicPaths) { this.publicPaths = publicPaths; }

        public static class Client {
            private String clientId;
            private String clientSecret;

            public String getClientId() { return clientId; }
            public void setClientId(String clientId) { this.clientId = clientId; }
            public String getClientSecret() { return clientSecret; }
            public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        }
    }
}
