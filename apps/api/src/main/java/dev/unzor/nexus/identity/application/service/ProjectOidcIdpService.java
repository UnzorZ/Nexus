package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.OidcFederationProperties;
import dev.unzor.nexus.identity.api.dto.GoogleIdpSummary;
import dev.unzor.nexus.identity.api.requests.SaveGoogleIdpRequest;
import dev.unzor.nexus.identity.domain.entity.ProjectOidcIdp;
import dev.unzor.nexus.identity.persistence.repository.ProjectOidcIdpRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.security.OidcFederationCrypto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads and persists the per-project Google IdP configuration (panel surface). The client
 * secret is encrypted at rest and never returned; on update a blank secret keeps the
 * existing value (the "leave blank to keep" pattern used by the SMTP settings).
 */
@Service
public class ProjectOidcIdpService {

    private final ProjectOidcIdpRepository repository;
    private final ProjectLookupService projectLookupService;
    private final OidcFederationCrypto crypto;
    private final OidcFederationProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectOidcIdpService(
            ProjectOidcIdpRepository repository,
            ProjectLookupService projectLookupService,
            OidcFederationCrypto crypto,
            OidcFederationProperties properties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.projectLookupService = projectLookupService;
        this.crypto = crypto;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public GoogleIdpSummary find(UUID projectId) {
        projectLookupService.requireById(projectId);
        return repository.findByProjectId(projectId)
                .map(GoogleIdpSummary::from)
                .orElseGet(() -> new GoogleIdpSummary(
                        projectId, properties.google().issuer(), null, properties.google().scope(),
                        false, false, false));
    }

    @Transactional
    public GoogleIdpSummary save(UUID projectId, SaveGoogleIdpRequest request, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectOidcIdp existing = repository.findByProjectId(projectId).orElse(null);
        String clientSecretEnc = resolveSecret(existing, request.clientSecret());
        String issuer = StringUtils.hasText(request.issuer()) ? request.issuer().trim() : properties.google().issuer();
        String scope = StringUtils.hasText(request.scope()) ? request.scope().trim() : properties.google().scope();

        if (existing == null) {
            existing = new ProjectOidcIdp(projectId, issuer, request.clientId().trim(),
                    clientSecretEnc, scope, request.enabled(), request.autoProvision());
        } else {
            existing.rewrite(issuer, request.clientId().trim(),
                    clientSecretEnc, scope, request.enabled(), request.autoProvision());
        }
        ProjectOidcIdp saved = repository.save(existing);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "federation.google.updated", "google_idp",
                Objects.toString(saved.getProjectId(), null), actorAccountId,
                Map.of("enabled", String.valueOf(saved.isEnabled()),
                        "autoProvision", String.valueOf(saved.isAutoProvision()))));
        return GoogleIdpSummary.from(saved);
    }

    @Transactional
    public void delete(UUID projectId, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        repository.findByProjectId(projectId).ifPresent(idp -> {
            repository.delete(idp);
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "federation.google.deleted", "google_idp",
                    projectId.toString(), actorAccountId, Map.of()));
        });
    }

    private String resolveSecret(ProjectOidcIdp existing, String clientSecret) {
        if (StringUtils.hasText(clientSecret)) {
            return crypto.encrypt(clientSecret);
        }
        if (existing != null) {
            return existing.getClientSecretEnc();
        }
        throw new IllegalArgumentException("clientSecret is required when configuring Google for the first time.");
    }
}
