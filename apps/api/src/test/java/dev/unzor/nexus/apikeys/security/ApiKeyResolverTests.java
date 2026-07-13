package dev.unzor.nexus.apikeys.security;

import dev.unzor.nexus.apikeys.domain.entity.ProjectApiKey;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyDisabledException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyExpiredException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyInvalidException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyProjectNotOperationalException;
import dev.unzor.nexus.apikeys.persistence.repository.ProjectApiKeyRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyResolverTests {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID KEY_ID = UUID.randomUUID();

    private final ProjectApiKeyRepository repository = mock(ProjectApiKeyRepository.class);
    private final ApiKeyHasher hasher = new ApiKeyHasher();
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final ApiKeyResolver resolver = new ApiKeyResolver(repository, hasher, projectLookupService);

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
        verify(projectLookupService).requireOperationalById(PROJECT_ID);
        verify(repository).save(key);
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
        verify(projectLookupService, never()).requireOperationalById(PROJECT_ID);
        verify(repository, never()).save(key);
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
        verify(projectLookupService, never()).requireOperationalById(PROJECT_ID);
        verify(repository, never()).save(key);
    }

    @Test
    void resolveRejectsANonOperationalProjectBeforeUpdatingLastUsed() {
        ApiKeyHasher.GeneratedKey generated = hasher.generate("shop");
        ProjectApiKey key = withId(new ProjectApiKey(
                PROJECT_ID, "ci", generated.keyPrefix(), generated.keyHash(),
                List.of(), null, null), KEY_ID);
        when(repository.findByKeyPrefix(generated.keyPrefix())).thenReturn(List.of(key));
        doThrow(new ProjectNotOperationalException(PROJECT_ID, ProjectStatus.ARCHIVED))
                .when(projectLookupService).requireOperationalById(PROJECT_ID);

        assertThatThrownBy(() -> resolver.resolve(generated.fullKey()))
                .isInstanceOf(ApiKeyProjectNotOperationalException.class)
                .hasCauseInstanceOf(ProjectNotOperationalException.class);
        assertThat(key.getLastUsedAt()).isNull();
        verify(repository, never()).save(key);
    }

    @Test
    void rawKeyForNonOperationalProjectAuditsTheValidatedKeyId() throws Exception {
        ApiKeyResolver rejectingResolver = mock(ApiKeyResolver.class);
        InstanceTokenService instanceTokenService = mock(InstanceTokenService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(rejectingResolver.resolve("raw-key"))
                .thenThrow(new ApiKeyProjectNotOperationalException(
                        KEY_ID, PROJECT_ID,
                        new ProjectNotOperationalException(PROJECT_ID, ProjectStatus.ARCHIVED)));
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                rejectingResolver, instanceTokenService, projectLookupService,
                new ProjectApiProblemWriter(new ObjectMapper()), eventPublisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/whoami");
        request.addHeader(ApiKeyAuthenticationFilter.HEADER, "raw-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"project_not_operational\"");
        ArgumentCaptor<AuditEvent> auditEvent = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(auditEvent.capture());
        assertThat(auditEvent.getValue().projectId()).isEqualTo(PROJECT_ID);
        assertThat(auditEvent.getValue().resourceId()).isEqualTo(KEY_ID.toString());
        assertThat(auditEvent.getValue().metadata()).containsEntry("reason", "project_not_operational");
    }

    @Test
    void instanceTokenForMissingProjectIsInvalidInsteadOfLeakingA404() throws Exception {
        InstanceTokenService instanceTokenService = mock(InstanceTokenService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ResolvedApiKey resolved = new ResolvedApiKey(PROJECT_ID, KEY_ID, "nxs_shop_test", List.of());
        when(instanceTokenService.resolve("instance-token")).thenReturn(Optional.of(resolved));
        doThrow(new ProjectNotFoundException(PROJECT_ID.toString()))
                .when(projectLookupService).requireOperationalById(PROJECT_ID);
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                resolver, instanceTokenService, projectLookupService,
                new ProjectApiProblemWriter(new ObjectMapper()), eventPublisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/whoami");
        request.addHeader(ApiKeyAuthenticationFilter.TOKEN_HEADER, "instance-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"invalid_instance_token\"");
        verify(eventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    void instanceTokenForNonOperationalProjectIsForbiddenAndAudited() throws Exception {
        InstanceTokenService instanceTokenService = mock(InstanceTokenService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ResolvedApiKey resolved = new ResolvedApiKey(PROJECT_ID, KEY_ID, "nxs_shop_test", List.of());
        when(instanceTokenService.resolve("instance-token")).thenReturn(Optional.of(resolved));
        doThrow(new ProjectNotOperationalException(PROJECT_ID, ProjectStatus.SUSPENDED))
                .when(projectLookupService).requireOperationalById(PROJECT_ID);
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                resolver, instanceTokenService, projectLookupService,
                new ProjectApiProblemWriter(new ObjectMapper()), eventPublisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/whoami");
        request.addHeader(ApiKeyAuthenticationFilter.TOKEN_HEADER, "instance-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"project_not_operational\"");
        verify(eventPublisher).publishEvent(any(AuditEvent.class));
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
