package dev.unzor.nexus.identity.persistence;

import dev.unzor.nexus.identity.application.service.ProjectOauthClientToRegisteredClientMapper;
import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.domain.enums.OauthClientStatus;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.projects.application.service.ResolveProjectBySlugService;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.transaction.annotation.Transactional;

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
 * <p><b>Aislamiento entre proyectos (remediación de auditoría, hallazgo crítico):</b>
 * bajo un issuer por-realm {@code {origin}/p/{slug}}, {@code findById} y
 * {@code findByClientId} sólo sirven clientes de ese proyecto — validan
 * {@code client.projectId == issuer.projectId} (resuelto vía
 * {@link AuthorizationServerContextHolder}) y nunca caen al repo global. Así un
 * cliente de otro realm (o el cliente técnico global) no se resuelve bajo
 * {@code /p/{slug}/}: SAS lo ve como inexistente y rechaza authorize/token.
 * Sin contexto o con issuer global (arranque, issuer técnico) se conserva el
 * comportamiento compuesto (proyecto primero, global después).</p>
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
    private final ProjectLookupService projectLookupService;

    public CompositeRegisteredClientRepository(
            ProjectOauthClientRepository projectRepository,
            ProjectOauthClientToRegisteredClientMapper mapper,
            JdbcRegisteredClientRepository global,
            JdbcTemplate jdbc,
            ResolveProjectBySlugService slugResolver,
            ProjectLookupService projectLookupService
    ) {
        this.projectRepository = projectRepository;
        this.mapper = mapper;
        this.global = global;
        this.jdbc = jdbc;
        this.slugResolver = slugResolver;
        this.projectLookupService = projectLookupService;
    }

    @Override
    @Transactional
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
        final UUID projectId;
        try {
            projectId = slugResolver.resolveOperational(slug).projectId();
            // Hold the shared project-row lock until the DCR INSERT/UPDATE commits.
            // ArchiveProjectService must acquire the conflicting write lock, so it
            // cannot interleave between the operational check and client persistence.
            projectLookupService.lockOperationalById(projectId);
        } catch (ProjectNotFoundException | ProjectNotOperationalException unavailableProject) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_REQUEST,
                    "The issuer project is not operational.",
                    "https://datatracker.ietf.org/doc/html/rfc7591#section-3.2.2"),
                    unavailableProject);
        }
        UUID id;
        try {
            id = UUID.fromString(rc.getId());
        } catch (IllegalArgumentException notAUuid) {
            throw new IllegalStateException("DCR client id is not a UUID: " + rc.getId());
        }
        // El provider de DCR de SAS pre-codifica el secreto con el DelegatingPasswordEncoder
        // ("{bcrypt}$2a$…"). El client-auth lo verifica con el BCryptPasswordEncoder crudo del
        // contexto (como los clientes del panel/dispositivo). Re-codificar aquí doble-hasharía
        // → 401 invalid_client al autenticar el cliente DCR. Descartamos el prefijo "{bcrypt}"
        // y guardamos el bcrypt crudo tal como lo emitió SAS.
        String secretHash = stripDelegatingPrefix(rc.getClientSecret());
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
        RealmIssuer realm = currentRealmIssuer();
        try {
            UUID uuid = UUID.fromString(id);
            var projectClient = projectRepository.findById(uuid);
            if (projectClient.isEmpty()) {
                return realm.isRealm() ? null : global.findById(id);
            }
            return mapIfUsable(projectClient.get(), realm);
        } catch (IllegalArgumentException notAUuid) {
            // id no-UUID = cliente bootstrap global. Bajo un realm no se sirve.
            return realm.isRealm() ? null : global.findById(id);
        }
    }

    /**
     * Resolves the client needed to deserialize an existing JDBC authorization.
     * Project operational state is deliberately checked by
     * {@link ProjectOperationalOAuth2AuthorizationService} after hydration: returning
     * {@code null} here makes SAS convert an otherwise valid authorization row into a
     * {@code DataRetrievalFailureException} before the runtime gate can return a native
     * OAuth invalid-token/invalid-grant result.
     *
     * <p>Client status and realm isolation still apply. Only the project lifecycle
     * check is deferred.</p>
     */
    public RegisteredClient findByIdForAuthorizationHydration(String id) {
        RealmIssuer realm = currentExistingRealmIssuer();
        try {
            UUID uuid = UUID.fromString(id);
            var projectClient = projectRepository.findById(uuid);
            if (projectClient.isEmpty()) {
                return realm.isRealm() ? null : global.findById(id);
            }
            ProjectOauthClient client = projectClient.get();
            if (!isActive(client) || !belongsToRealm(client, realm)) {
                return null;
            }
            return mapper.toRegisteredClient(client);
        } catch (IllegalArgumentException notAUuid) {
            return realm.isRealm() ? null : global.findById(id);
        }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        RealmIssuer realm = currentRealmIssuer();
        var projectClient = projectRepository.findByClientId(clientId);
        if (projectClient.isEmpty()) {
            return realm.isRealm() ? null : global.findByClientId(clientId);
        }
        return mapIfUsable(projectClient.get(), realm);
    }

    /**
     * Realm del issuer en curso (vía {@link AuthorizationServerContextHolder}):
     * {@code GLOBAL} si no hay contexto o el issuer no es por-realm; o {@code REALM}
     * con el projectId resuelto del slug (null si el slug no existe — aun así se
     * trata como realm para no servir el cliente técnico global ni de otros proyectos
     * bajo {@code /p/{slug}/}).
     */
    private RealmIssuer currentRealmIssuer() {
        return currentRealmIssuer(true);
    }

    private RealmIssuer currentExistingRealmIssuer() {
        return currentRealmIssuer(false);
    }

    private RealmIssuer currentRealmIssuer(boolean requireOperational) {
        var ctx = AuthorizationServerContextHolder.getContext();
        if (ctx == null) {
            return RealmIssuer.GLOBAL;
        }
        String issuer = ctx.getIssuer();
        if (issuer == null) {
            return RealmIssuer.GLOBAL;
        }
        int idx = issuer.lastIndexOf("/p/");
        if (idx < 0) {
            return RealmIssuer.GLOBAL;
        }
        String slug = issuer.substring(idx + 3);
        try {
            UUID projectId = requireOperational
                    ? slugResolver.resolveOperational(slug).projectId()
                    : slugResolver.resolve(slug).projectId();
            return new RealmIssuer(true, projectId);
        } catch (ProjectNotFoundException | ProjectNotOperationalException unavailableRealm) {
            return new RealmIssuer(true, null);
        }
    }

    /**
     * A project-backed client shadows the global repository even when it cannot be
     * served. This prevents an inactive project client from accidentally falling
     * through to a global client with the same id/client-id.
     */
    private RegisteredClient mapIfUsable(ProjectOauthClient client, RealmIssuer realm) {
        if (!isActive(client) || !belongsToRealm(client, realm) || !isProjectOperational(client.getProjectId())) {
            return null;
        }
        return mapper.toRegisteredClient(client);
    }

    private boolean isProjectOperational(UUID projectId) {
        try {
            projectLookupService.requireOperationalById(projectId);
            return true;
        } catch (ProjectNotFoundException | ProjectNotOperationalException unavailableProject) {
            return false;
        }
    }

    /** Bajo un issuer por-realm sólo se sirve el cliente de ese proyecto; GLOBAL no filtra. */
    private static boolean belongsToRealm(ProjectOauthClient client, RealmIssuer realm) {
        if (!realm.isRealm()) {
            return true;
        }
        return realm.projectId() != null && realm.projectId().equals(client.getProjectId());
    }

    private record RealmIssuer(boolean isRealm, UUID projectId) {
        private static final RealmIssuer GLOBAL = new RealmIssuer(false, null);
    }

    /**
     * Descarta el prefijo del DelegatingPasswordEncoder ({@code "{bcrypt}"}) que el provider
     * de DCR de SAS añade al codificar el secreto, devolviendo el bcrypt crudo. {@code null}
     * para clientes públicos.
     */
    private static String stripDelegatingPrefix(String clientSecret) {
        if (clientSecret == null) {
            return null;
        }
        int end = clientSecret.indexOf('}');
        return (end > 0 && clientSecret.charAt(0) == '{') ? clientSecret.substring(end + 1) : clientSecret;
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
