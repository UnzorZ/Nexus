package dev.unzor.nexus.apikeys.application.service;

import dev.unzor.nexus.apikeys.api.dto.ApiKeyCreated;
import dev.unzor.nexus.apikeys.api.dto.ApiKeySummary;
import dev.unzor.nexus.apikeys.domain.entity.ProjectApiKey;
import dev.unzor.nexus.apikeys.domain.enums.ApiKeyStatus;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyNotFoundException;
import dev.unzor.nexus.apikeys.persistence.repository.ProjectApiKeyRepository;
import dev.unzor.nexus.apikeys.security.ApiKeyHasher;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de gestión de API keys de un proyecto (spec §9.3, §21.1). La
 * capa de seguridad está en {@link dev.unzor.nexus.apikeys.security.ApiKeyHasher}
 * y el secreto completo solo se devuelve al crear/rotar.
 */
@Service
public class ProjectApiKeysService {

    private final ProjectApiKeyRepository repository;
    private final ApiKeyHasher hasher;
    private final ProjectLookupService projectLookupService;

    public ProjectApiKeysService(
            ProjectApiKeyRepository repository,
            ApiKeyHasher hasher,
            ProjectLookupService projectLookupService
    ) {
        this.repository = repository;
        this.hasher = hasher;
        this.projectLookupService = projectLookupService;
    }

    @Transactional(readOnly = true)
    public List<ApiKeySummary> listForProject(UUID projectId) {
        projectLookupService.requireById(projectId);
        return repository.findAllByProjectId(projectId).stream()
                .map(ApiKeySummary::from)
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
        return new ApiKeyCreated(ApiKeySummary.from(key), generated.fullKey());
    }

    @Transactional
    public ApiKeySummary update(
            UUID projectId,
            UUID keyId,
            String name,
            ApiKeyStatus status,
            Instant expiresAt
    ) {
        projectLookupService.requireById(projectId);
        ProjectApiKey key = requireKey(projectId, keyId);
        key.rename(name);
        if (status == ApiKeyStatus.DISABLED) {
            key.disable();
        } else {
            key.enable();
        }
        key.expireAt(expiresAt);
        return ApiKeySummary.from(repository.save(key));
    }

    /**
     * Rotación: crea una key reemplazo (mismo nombre+scopes, secreto nuevo que se
     * devuelve una vez) y deshabilita la anterior.
     */
    @Transactional
    public ApiKeyCreated rotate(UUID projectId, UUID keyId) {
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
        return new ApiKeyCreated(ApiKeySummary.from(replacement), generated.fullKey());
    }

    @Transactional
    public void delete(UUID projectId, UUID keyId) {
        projectLookupService.requireById(projectId);
        ProjectApiKey key = requireKey(projectId, keyId);
        repository.delete(key);
    }

    private ProjectApiKey requireKey(UUID projectId, UUID keyId) {
        return repository.findByProjectIdAndId(projectId, keyId)
                .orElseThrow(() -> new ApiKeyNotFoundException(
                        "API key " + keyId + " not found in project " + projectId + "."));
    }
}
