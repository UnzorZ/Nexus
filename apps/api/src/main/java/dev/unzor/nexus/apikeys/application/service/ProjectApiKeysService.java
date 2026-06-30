package dev.unzor.nexus.apikeys.application.service;

import dev.unzor.nexus.apikeys.api.dto.ApiKeyCreated;
import dev.unzor.nexus.apikeys.api.dto.ApiKeySummary;
import dev.unzor.nexus.apikeys.application.events.ApiKeyAuditEvent;
import dev.unzor.nexus.apikeys.domain.entity.ProjectApiKey;
import dev.unzor.nexus.apikeys.domain.enums.ApiKeyStatus;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyNotFoundException;
import dev.unzor.nexus.apikeys.persistence.repository.ProjectApiKeyRepository;
import dev.unzor.nexus.apikeys.security.ApiKeyHasher;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casos de uso de gestión de API keys de un proyecto (spec §9.3, §21.1). La
 * capa de seguridad está en {@link dev.unzor.nexus.apikeys.security.ApiKeyHasher}
 * y el secreto completo solo se devuelve al crear/rotar. Cada mutation emite un
 * {@link ApiKeyAuditEvent} (ADR-0004) sin secretos.
 */
@Service
public class ProjectApiKeysService {

    private final ProjectApiKeyRepository repository;
    private final ApiKeyHasher hasher;
    private final ProjectLookupService projectLookupService;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectApiKeysService(
            ProjectApiKeyRepository repository,
            ApiKeyHasher hasher,
            ProjectLookupService projectLookupService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.hasher = hasher;
        this.projectLookupService = projectLookupService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ApiKeySummary> listForProject(UUID projectId) {
        String slug = projectLookupService.requireSlug(projectId);
        return repository.findAllByProjectId(projectId).stream()
                .map(key -> ApiKeySummary.from(key, "nxs_" + slug + "_" + key.getKeyPrefix()))
                .toList();
    }

    @Transactional
    public ApiKeyCreated create(
            UUID projectId,
            String name,
            List<String> scopes,
            Instant expiresAt,
            UUID creatorAccountId
    ) {
        String slug = projectLookupService.requireSlug(projectId);
        ApiKeyHasher.GeneratedKey generated = hasher.generate(slug);
        ProjectApiKey key = new ProjectApiKey(
                projectId, name, generated.keyPrefix(), generated.keyHash(), scopes, expiresAt, creatorAccountId);
        repository.saveAndFlush(key);
        String displayPrefix = "nxs_" + slug + "_" + generated.keyPrefix();
        audit("api_key.created", projectId, key.getId(), creatorAccountId,
                Map.of("name", name, "prefix", displayPrefix));
        return ApiKeyCreated.of(key, displayPrefix, generated.fullKey());
    }

    @Transactional
    public ApiKeySummary update(
            UUID projectId,
            UUID keyId,
            String name,
            ApiKeyStatus status,
            Instant expiresAt,
            UUID actorAccountId
    ) {
        String slug = projectLookupService.requireSlug(projectId);
        ProjectApiKey key = requireKey(projectId, keyId);
        ApiKeyStatus previousStatus = key.getStatus();
        key.rename(name);
        if (status == ApiKeyStatus.DISABLED) {
            key.disable();
        } else {
            key.enable();
        }
        key.expireAt(expiresAt);
        String displayPrefix = "nxs_" + slug + "_" + key.getKeyPrefix();
        ApiKeySummary summary = ApiKeySummary.from(repository.save(key), displayPrefix);

        String action;
        if (status == ApiKeyStatus.DISABLED && previousStatus != ApiKeyStatus.DISABLED) {
            action = "api_key.disabled";
        } else if (status == ApiKeyStatus.ACTIVE && previousStatus == ApiKeyStatus.DISABLED) {
            action = "api_key.enabled";
        } else {
            action = "api_key.updated";
        }
        audit(action, projectId, keyId, actorAccountId, Map.of("name", name));
        return summary;
    }

    /**
     * Rotación: crea una key reemplazo (mismo nombre+scopes, secreto nuevo que se
     * devuelve una vez) y deshabilita la anterior.
     */
    @Transactional
    public ApiKeyCreated rotate(UUID projectId, UUID keyId, UUID actorAccountId) {
        String slug = projectLookupService.requireSlug(projectId);
        ProjectApiKey old = requireKey(projectId, keyId);
        ApiKeyHasher.GeneratedKey generated = hasher.generate(slug);
        ProjectApiKey replacement = new ProjectApiKey(
                projectId,
                old.getName(),
                generated.keyPrefix(),
                generated.keyHash(),
                old.getScopes(),
                old.getExpiresAt(),
                old.getCreatedByAccountId());
        repository.saveAndFlush(replacement);
        old.disable();
        repository.save(old);
        String displayPrefix = "nxs_" + slug + "_" + generated.keyPrefix();
        audit("api_key.rotated", projectId, replacement.getId(), actorAccountId,
                Map.of("name", replacement.getName(), "prefix", displayPrefix,
                        "rotatedKeyId", keyId.toString()));
        return ApiKeyCreated.of(replacement, displayPrefix, generated.fullKey());
    }

    @Transactional
    public void delete(UUID projectId, UUID keyId, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectApiKey key = requireKey(projectId, keyId);
        repository.delete(key);
        audit("api_key.deleted", projectId, keyId, actorAccountId, Map.of("name", key.getName()));
    }

    private ProjectApiKey requireKey(UUID projectId, UUID keyId) {
        return repository.findByProjectIdAndId(projectId, keyId)
                .orElseThrow(() -> new ApiKeyNotFoundException(
                        "API key " + keyId + " not found in project " + projectId + "."));
    }

    private void audit(String action, UUID projectId, UUID keyId, UUID actorId, Map<String, Object> metadata) {
        eventPublisher.publishEvent(new ApiKeyAuditEvent(
                action, projectId, keyId, "NEXUS_ACCOUNT",
                actorId == null ? null : actorId.toString(), null, metadata, MDC.get("traceId")));
    }
}
