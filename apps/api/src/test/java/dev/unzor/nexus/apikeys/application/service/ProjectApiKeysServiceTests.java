package dev.unzor.nexus.apikeys.application.service;

import dev.unzor.nexus.apikeys.api.dto.ApiKeyCreated;
import dev.unzor.nexus.apikeys.api.dto.ApiKeySummary;
import dev.unzor.nexus.apikeys.application.events.ApiKeyAuditEvent;
import dev.unzor.nexus.apikeys.domain.entity.ProjectApiKey;
import dev.unzor.nexus.apikeys.domain.enums.ApiKeyStatus;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyNotFoundException;
import dev.unzor.nexus.apikeys.persistence.repository.ProjectApiKeyRepository;
import dev.unzor.nexus.apikeys.security.ApiKeyHasher;
import dev.unzor.nexus.apikeys.security.InstanceTokenService;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectApiKeysServiceTests {

    private final ProjectApiKeyRepository repository = mock(ProjectApiKeyRepository.class);
    private final ApiKeyHasher hasher = new ApiKeyHasher();
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final InstanceTokenService instanceTokenService = mock(InstanceTokenService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProjectApiKeysService service =
            new ProjectApiKeysService(repository, hasher, projectLookupService, instanceTokenService, eventPublisher);

    @Test
    void createReturnsFlatCreatedKeyWithSecret() {
        UUID projectId = UUID.randomUUID();
        when(projectLookupService.requireSlug(projectId)).thenReturn("shop");
        when(repository.saveAndFlush(any(ProjectApiKey.class))).thenAnswer(i -> i.getArgument(0));

        ApiKeyCreated created = service.create(
                projectId, "ci", List.of("registry:heartbeat"), null, UUID.randomUUID());

        assertThat(created.secret()).startsWith("nxs_shop_");
        assertThat(created.name()).isEqualTo("ci");
        assertThat(created.scopes()).containsExactly("registry:heartbeat");
        assertThat(created.status()).isEqualTo(ApiKeyStatus.ACTIVE);
        verify(repository).saveAndFlush(any(ProjectApiKey.class));
        verify(eventPublisher).publishEvent(any(ApiKeyAuditEvent.class));
    }

    @Test
    void listReturnsSummariesWithoutSecret() {
        UUID projectId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ProjectApiKey key = withId(new ProjectApiKey(
                projectId, "ci", "partial123", "hash", List.of("registry:heartbeat"), null, null), keyId);
        when(projectLookupService.requireSlug(projectId)).thenReturn("shop");
        when(repository.findAllByProjectId(projectId)).thenReturn(List.of(key));

        List<ApiKeySummary> result = service.listForProject(projectId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(keyId);
        assertThat(result.get(0).prefix()).isEqualTo("nxs_shop_partial123");
    }

    @Test
    void updateDisablesAndEmitsDisabledAudit() {
        UUID projectId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ProjectApiKey key = withId(new ProjectApiKey(
                projectId, "ci", "prefix", "hash", List.of(), null, null), keyId);
        when(repository.findByProjectIdAndId(projectId, keyId)).thenReturn(Optional.of(key));
        when(projectLookupService.requireSlug(projectId)).thenReturn("shop");
        when(repository.save(any(ProjectApiKey.class))).thenAnswer(i -> i.getArgument(0));
        Instant expiry = Instant.now().plusSeconds(3600);

        ApiKeySummary updated = service.update(projectId, keyId, "renamed", ApiKeyStatus.DISABLED, expiry,
                UUID.randomUUID());

        assertThat(updated.status()).isEqualTo(ApiKeyStatus.DISABLED);
        assertThat(key.getStatus()).isEqualTo(ApiKeyStatus.DISABLED);
        verify(instanceTokenService).revokeFor(keyId);
        verify(eventPublisher).publishEvent(any(ApiKeyAuditEvent.class));
    }

    @Test
    void rotateCreatesReplacementAndDisablesOld() {
        UUID projectId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        ProjectApiKey old = withId(new ProjectApiKey(
                projectId, "ci", "oldprefix", "oldhash", List.of("a:b"), null, creator), keyId);
        when(repository.findByProjectIdAndId(projectId, keyId)).thenReturn(Optional.of(old));
        when(projectLookupService.requireSlug(projectId)).thenReturn("shop");
        when(repository.saveAndFlush(any(ProjectApiKey.class))).thenAnswer(i -> i.getArgument(0));

        ApiKeyCreated created = service.rotate(projectId, keyId, UUID.randomUUID());

        assertThat(created.secret()).startsWith("nxs_shop_");
        assertThat(created.scopes()).containsExactly("a:b");
        assertThat(old.getStatus()).isEqualTo(ApiKeyStatus.DISABLED);
        verify(repository).save(old);
        verify(instanceTokenService).revokeFor(keyId);
        verify(eventPublisher).publishEvent(any(ApiKeyAuditEvent.class));
    }

    @Test
    void deleteRemovesKey() {
        UUID projectId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ProjectApiKey key = withId(new ProjectApiKey(
                projectId, "ci", "prefix", "hash", List.of(), null, null), keyId);
        when(repository.findByProjectIdAndId(projectId, keyId)).thenReturn(Optional.of(key));

        service.delete(projectId, keyId, UUID.randomUUID());

        verify(repository).delete(key);
        verify(instanceTokenService).revokeFor(keyId);
        verify(eventPublisher).publishEvent(any(ApiKeyAuditEvent.class));
    }

    @Test
    void updateThrowsWhenKeyMissing() {
        UUID projectId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        when(repository.findByProjectIdAndId(projectId, keyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(projectId, keyId, "x", ApiKeyStatus.ACTIVE, null, UUID.randomUUID()))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> T withId(T entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
