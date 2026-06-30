package dev.unzor.nexus.apikeys.security;

import dev.unzor.nexus.apikeys.domain.entity.ProjectApiKey;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyDisabledException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyExpiredException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyInvalidException;
import dev.unzor.nexus.apikeys.persistence.repository.ProjectApiKeyRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyResolverTests {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID KEY_ID = UUID.randomUUID();

    private final ProjectApiKeyRepository repository = mock(ProjectApiKeyRepository.class);
    private final ApiKeyHasher hasher = new ApiKeyHasher();
    private final ApiKeyResolver resolver = new ApiKeyResolver(repository, hasher);

    @Test
    void resolveReturnsProjectAndScopesForAValidKey() {
        ApiKeyHasher.GeneratedKey generated = hasher.generate("shop");
        ProjectApiKey key = withId(new ProjectApiKey(
                PROJECT_ID, "ci", generated.keyPrefix(), generated.keyHash(),
                List.of("registry:heartbeat"), null, null), KEY_ID);
        when(repository.findByKeyPrefix(generated.keyPrefix())).thenReturn(List.of(key));

        ResolvedApiKey resolved = resolver.resolve(generated.fullKey());

        assertThat(resolved.projectId()).isEqualTo(PROJECT_ID);
        assertThat(resolved.keyId()).isEqualTo(KEY_ID);
        assertThat(resolved.scopes()).containsExactly("registry:heartbeat");
    }

    @Test
    void resolveThrowsInvalidWhenNoCandidateMatches() {
        when(repository.findByKeyPrefix(anyString())).thenReturn(List.of());
        assertThatThrownBy(() -> resolver.resolve("nxs_shop_doesnotmatter"))
                .isInstanceOf(ApiKeyInvalidException.class);
    }

    @Test
    void resolveThrowsInvalidForMalformedKey() {
        assertThatThrownBy(() -> resolver.resolve("garbage"))
                .isInstanceOf(ApiKeyInvalidException.class);
    }

    @Test
    void resolveThrowsDisabled() {
        ApiKeyHasher.GeneratedKey generated = hasher.generate("shop");
        ProjectApiKey key = withId(new ProjectApiKey(
                PROJECT_ID, "ci", generated.keyPrefix(), generated.keyHash(),
                List.of(), null, null), KEY_ID);
        key.disable();
        when(repository.findByKeyPrefix(generated.keyPrefix())).thenReturn(List.of(key));

        assertThatThrownBy(() -> resolver.resolve(generated.fullKey()))
                .isInstanceOf(ApiKeyDisabledException.class);
    }

    @Test
    void resolveThrowsExpired() {
        ApiKeyHasher.GeneratedKey generated = hasher.generate("shop");
        ProjectApiKey key = withId(new ProjectApiKey(
                PROJECT_ID, "ci", generated.keyPrefix(), generated.keyHash(),
                List.of(), Instant.now().minusSeconds(60), null), KEY_ID);
        when(repository.findByKeyPrefix(generated.keyPrefix())).thenReturn(List.of(key));

        assertThatThrownBy(() -> resolver.resolve(generated.fullKey()))
                .isInstanceOf(ApiKeyExpiredException.class);
    }

    @Test
    void resolveSelectsTheMatchingCandidateAmongPrefixCollisions() {
        ApiKeyHasher.GeneratedKey match = hasher.generate("shop");
        ApiKeyHasher.GeneratedKey other = hasher.generate("shop");
        ProjectApiKey matching = withId(new ProjectApiKey(
                PROJECT_ID, "a", match.keyPrefix(), match.keyHash(), List.of("x:y"), null, null), KEY_ID);
        // Same prefix string but a different hash (simulated collision).
        ProjectApiKey colliding = withId(new ProjectApiKey(
                UUID.randomUUID(), "b", match.keyPrefix(), other.keyHash(), List.of(), null, null),
                UUID.randomUUID());
        when(repository.findByKeyPrefix(match.keyPrefix())).thenReturn(List.of(colliding, matching));

        ResolvedApiKey resolved = resolver.resolve(match.fullKey());
        assertThat(resolved.keyId()).isEqualTo(KEY_ID);
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

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
