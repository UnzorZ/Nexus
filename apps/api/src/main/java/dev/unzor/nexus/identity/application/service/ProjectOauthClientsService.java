package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.OauthClientCreated;
import dev.unzor.nexus.identity.api.dto.OauthClientSummary;
import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.domain.enums.OauthClientStatus;
import dev.unzor.nexus.identity.domain.exception.OauthClientNotFoundException;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Casos de uso de gestión de clientes OAuth de un proyecto (spec §9.6, §15.3).
 * El {@code client_id} es globalmente único; el secreto se genera sólo para
 * clientes confidenciales y se devuelve una vez (hasheado con el
 * {@link PasswordEncoder} compartido, prefijo {@code {bcrypt}} para que el AS lo
 * verifique como el cliente bootstrap). Los clientes públicos (sin secreto)
 * obligan a PKCE. Cada mutación emite un {@link AuditEvent} sin secretos.
 */
@Service
public class ProjectOauthClientsService {

    private static final List<String> DEFAULT_GRANT_TYPES = List.of("authorization_code", "refresh_token");
    private static final List<String> DEFAULT_SCOPES = List.of("openid");

    private final ProjectOauthClientRepository repository;
    private final OauthClientSecretGenerator secretGenerator;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectOauthClientsService(
            ProjectOauthClientRepository repository,
            OauthClientSecretGenerator secretGenerator,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.secretGenerator = secretGenerator;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<OauthClientSummary> listForProject(UUID projectId) {
        return repository.findAllByProjectId(projectId).stream()
                .map(OauthClientSummary::from)
                .toList();
    }

    @Transactional
    public OauthClientCreated create(
            UUID projectId,
            String name,
            List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            List<String> grantTypes,
            List<String> scopes,
            boolean requirePkce,
            boolean confidential,
            boolean consentRequired,
            String backchannelLogoutUri,
            UUID actorAccountId
    ) {
        List<String> effectiveGrants = (grantTypes == null || grantTypes.isEmpty()) ? DEFAULT_GRANT_TYPES : grantTypes;
        List<String> effectiveScopes = (scopes == null || scopes.isEmpty()) ? DEFAULT_SCOPES : scopes;
        // Los clientes públicos (sin secreto) DEBEN usar PKCE (spec §9.6).
        boolean effectiveRequirePkce = confidential ? requirePkce : true;

        String clientId = secretGenerator.generateClientId();
        String rawSecret = null;
        String secretHash = null;
        if (confidential) {
            rawSecret = secretGenerator.generateClientSecret();
            // El bean PasswordEncoder es un BCryptPasswordEncoder plano (compartido con
            // las passwords de ProjectUser, que se almacenan sin prefijo). SAS verifica el
            // client_secret con ese mismo bean, así que el hash debe ir SIN el prefijo
            // "{bcrypt}": con prefijo, BCryptPasswordEncoder rechaza ("does not look like
            // BCrypt") y el token endpoint devuelve invalid_client.
            secretHash = passwordEncoder.encode(rawSecret);
        }

        ProjectOauthClient client = new ProjectOauthClient(
                projectId, clientId, secretHash, name,
                redirectUris, postLogoutRedirectUris, effectiveGrants, effectiveScopes,
                effectiveRequirePkce, consentRequired, actorAccountId);
        client.updateBackchannelLogoutUri(backchannelLogoutUri);
        ProjectOauthClient saved = repository.saveAndFlush(client);
        audit("oauth_client.created", projectId, saved.getId(), actorAccountId,
                Map.of("name", saved.getName(), "client_id", saved.getClientId()));
        return OauthClientCreated.of(saved, rawSecret);
    }

    @Transactional
    public OauthClientSummary update(
            UUID projectId, UUID clientId,
            String name,
            List<String> redirectUris, List<String> postLogoutRedirectUris, List<String> scopes,
            OauthClientStatus status,
            String backchannelLogoutUri,
            UUID actorAccountId
    ) {
        ProjectOauthClient client = require(projectId, clientId);
        client.rename(name);
        client.updateRedirectUris(redirectUris, postLogoutRedirectUris);
        client.updateScopes(scopes);
        client.updateBackchannelLogoutUri(backchannelLogoutUri);
        if (status == OauthClientStatus.DISABLED) {
            client.disable();
        } else {
            client.enable();
        }
        ProjectOauthClient saved = repository.save(client);
        audit("oauth_client.updated", projectId, saved.getId(), actorAccountId,
                Map.of("name", saved.getName()));
        return OauthClientSummary.from(saved);
    }

    @Transactional
    public OauthClientCreated rotateSecret(UUID projectId, UUID clientId, UUID actorAccountId) {
        ProjectOauthClient client = require(projectId, clientId);
        if (!client.isConfidential()) {
            // No tiene sentido rotar el secreto de un cliente público.
            throw new IllegalStateException("Cannot rotate secret of a public OAuth client.");
        }
        String rawSecret = secretGenerator.generateClientSecret();
        client.rotateSecret(passwordEncoder.encode(rawSecret));
        ProjectOauthClient saved = repository.save(client);
        audit("oauth_client.rotated", projectId, saved.getId(), actorAccountId,
                Map.of("name", saved.getName(), "client_id", saved.getClientId()));
        return OauthClientCreated.of(saved, rawSecret);
    }

    @Transactional
    public OauthClientSummary disable(UUID projectId, UUID clientId, UUID actorAccountId) {
        ProjectOauthClient client = require(projectId, clientId);
        client.disable();
        ProjectOauthClient saved = repository.save(client);
        audit("oauth_client.disabled", projectId, saved.getId(), actorAccountId,
                Map.of("name", saved.getName()));
        return OauthClientSummary.from(saved);
    }

    @Transactional
    public void delete(UUID projectId, UUID clientId, UUID actorAccountId) {
        ProjectOauthClient client = require(projectId, clientId);
        repository.delete(client);
        audit("oauth_client.deleted", projectId, clientId, actorAccountId,
                Map.of("name", client.getName(), "client_id", client.getClientId()));
    }

    private ProjectOauthClient require(UUID projectId, UUID clientId) {
        return repository.findByProjectIdAndId(projectId, clientId)
                .orElseThrow(() -> new OauthClientNotFoundException(projectId, clientId));
    }

    private void audit(String action, UUID projectId, UUID clientId, UUID actorId, Map<String, Object> metadata) {
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, action, "oauth_client", Objects.toString(clientId, null), actorId, metadata));
    }
}
