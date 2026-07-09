package dev.unzor.nexus.identity.persistence;

import dev.unzor.nexus.identity.application.service.ProjectOauthClientToRegisteredClientMapper;
import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.domain.enums.OauthClientStatus;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import dev.unzor.nexus.projects.application.service.ResolveProjectBySlugService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link RegisteredClientRepository} compuesto que sirve a la vez a los clientes
 * OAuth <b>por proyecto</b> (tabla {@code project_oauth_clients}) y al cliente
 * técnico <b>global</b> (tabla SAS-default {@code oauth2_registered_client},
 * reconciliado por {@code OidcRegisteredClientBootstrap}).
 *
 * <p>Las búsquedas prueban primero el repo de proyecto; si no encuentran, delegan
 * en el {@link JdbcRegisteredClientRepository} global (donde vive el cliente
 * bootstrap).</p>
 *
 * <p><b>save</b> discrimina por el issuer del contexto del AS:
 * <ul>
 *   <li>Sin contexto (bootstrap runner en arranque) o issuer global → persiste en la tabla
 *       global (cliente técnico).</li>
 *   <li>Issuer por-realm {@code {origin}/p/{slug}} (registro dinámico DCR en
 *       {@code /p/{slug}/connect/register}) → persiste el nuevo cliente en
 *       {@code project_oauth_clients} del proyecto {slug}, con el mismo id que el
 *       {@link RegisteredClient} (las autorizaciones se keyean por ese id) y el
 *       client_secret hasheado (bcrypt, mismo encoder que la app). Así el DCR es
 *       <b>project-scoped</b> y el cliente queda sujeto al realm del proyecto.</li>
 * </ul>
 *
 * <p>El aislamiento entre proyectos no depende de este repositorio: cada cliente
 * tiene un id (UUID) y un client_id globalmente únicos, y las autorizaciones se
 * keyean por {@code registered_client_id} + {@code principal_name}.</p>
 */
public class CompositeRegisteredClientRepository implements RegisteredClientRepository {

    /**
     * Scopes OIDC por defecto asignados a un cliente registrado dinámicamente (DCR) que no
     * declara scopes. SAS 7.0.5 rechaza {@code scope} en el registro dinámico por diseño
     * ("scope must not be set during Dynamic Client Registration") — el AS controla los
     * scopes, no el cliente auto-registrado. Sin embargo, el converter de SAS no aplica
     * scopes por defecto, así que un cliente DCR "en bruto" llegaría con cero scopes y no
     * podría completar un login OIDC (necesita {@code openid}). En Nexus los scopes OAuth
     * son sólo los estándar OIDC ({@code openid}, {@code profile}) — los permisos viajan en
     * el claim {@code permissions} (vía {@code ProjectIdTokenCustomizer}), no como scopes —,
     * de modo que este default es suficiente para OIDC y no concede nada sensible.
     */
    static final java.util.Set<String> DEFAULT_DCR_SCOPES = java.util.Set.of("openid", "profile");

    private final ProjectOauthClientRepository projectRepository;
    private final ProjectOauthClientToRegisteredClientMapper mapper;
    private final JdbcRegisteredClientRepository global;
    private final JdbcTemplate jdbc;
    private final ResolveProjectBySlugService slugResolver;
    private final PasswordEncoder passwordEncoder;

    public CompositeRegisteredClientRepository(
            ProjectOauthClientRepository projectRepository,
            ProjectOauthClientToRegisteredClientMapper mapper,
            JdbcRegisteredClientRepository global,
            JdbcTemplate jdbc,
            ResolveProjectBySlugService slugResolver,
            PasswordEncoder passwordEncoder
    ) {
        this.projectRepository = projectRepository;
        this.mapper = mapper;
        this.global = global;
        this.jdbc = jdbc;
        this.slugResolver = slugResolver;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        String issuer = null;
        var ctx = AuthorizationServerContextHolder.getContext();
        if (ctx != null) {
            issuer = ctx.getIssuer();
        }
        int realmIdx = issuer == null ? -1 : issuer.lastIndexOf("/p/");
        if (realmIdx > 0) {
            // DCR per-issuer → persiste como cliente del proyecto {slug}.
            String slug = issuer.substring(realmIdx + 3);
            persistProjectClient(registeredClient, slug);
        } else {
            // Bootstrap runner (sin contexto) o issuer global → tabla global.
            global.save(registeredClient);
        }
    }

    private void persistProjectClient(RegisteredClient rc, String slug) {
        UUID projectId = slugResolver.resolve(slug).projectId();
        UUID id;
        try {
            id = UUID.fromString(rc.getId());
        } catch (IllegalArgumentException notAUuid) {
            throw new IllegalStateException("DCR client id is not a UUID: " + rc.getId());
        }
        String secretHash = rc.getClientSecret() == null ? null : passwordEncoder.encode(rc.getClientSecret());
        String name = (rc.getClientName() != null && !rc.getClientName().isBlank())
                ? rc.getClientName() : rc.getClientId();
        String redirectUris = String.join("\n", rc.getRedirectUris());
        String postLogoutRedirectUris = String.join("\n", rc.getPostLogoutRedirectUris());
        String grantTypes = rc.getAuthorizationGrantTypes().stream()
                .map(AuthorizationGrantType::getValue).collect(Collectors.joining("\n"));
        // DCR sin scopes declarados → default OIDC (ver DEFAULT_DCR_SCOPES). SAS rechaza
        // `scope` en el registro, así que un cliente DCR llega siempre vacío aquí.
        java.util.Set<String> effectiveScopes = rc.getScopes().isEmpty()
                ? DEFAULT_DCR_SCOPES : rc.getScopes();
        String scopes = String.join("\n", effectiveScopes);
        boolean requirePkce = rc.getClientSettings() != null && rc.getClientSettings().isRequireProofKey();
        boolean consentRequired = rc.getClientSettings() != null && rc.getClientSettings().isRequireAuthorizationConsent();
        Timestamp now = Timestamp.from(Instant.now());

        // DCR create (INSERT) vs config update (UPDATE) por id.
        int updated = jdbc.update(
                "UPDATE project_oauth_clients SET client_secret_hash = ?, name = ?, redirect_uris = ?, "
                        + "post_logout_redirect_uris = ?, grant_types = ?, scopes = ?, require_pkce = ?, "
                        + "consent_required = ?, updated_at = ? WHERE id = ?",
                secretHash, name, redirectUris, postLogoutRedirectUris, grantTypes, scopes,
                requirePkce, consentRequired, now, id);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO project_oauth_clients (id, project_id, client_id, client_secret_hash, name, "
                            + "redirect_uris, post_logout_redirect_uris, grant_types, scopes, require_pkce, "
                            + "consent_required, status, created_by_account_id, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)",
                    id, projectId, rc.getClientId(), secretHash, name, redirectUris, postLogoutRedirectUris,
                    grantTypes, scopes, requirePkce, consentRequired, null, now, now);
        }
    }

    @Override
    public RegisteredClient findById(String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return projectRepository.findById(uuid)
                    .filter(CompositeRegisteredClientRepository::isActive)
                    .map(mapper::toRegisteredClient)
                    .orElseGet(() -> global.findById(id));
        } catch (IllegalArgumentException notAUuid) {
            // El cliente bootstrap usa un id configurado (string), no un UUID.
            return global.findById(id);
        }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return projectRepository.findByClientId(clientId)
                .filter(CompositeRegisteredClientRepository::isActive)
                .map(mapper::toRegisteredClient)
                .orElseGet(() -> global.findByClientId(clientId));
    }

    /**
     * Un cliente DISABLED no se expone como {@link RegisteredClient}: SAS lo tratará
     * como "no encontrado" y rechazará nuevos authorize, intercambio de code y uso de
     * refresh tokens (todos requieren resolver el cliente). Los access tokens en vuelo
     * caducan solos con su TTL corto.
     */
    private static boolean isActive(ProjectOauthClient client) {
        return client.getStatus() == OauthClientStatus.ACTIVE;
    }
}
