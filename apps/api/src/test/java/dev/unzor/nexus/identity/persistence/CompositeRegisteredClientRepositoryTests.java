package dev.unzor.nexus.identity.persistence;

import dev.unzor.nexus.identity.application.service.ProjectOauthClientToRegisteredClientMapper;
import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeRegisteredClientRepositoryTests {

    private final ProjectOauthClientRepository projectRepo = mock(ProjectOauthClientRepository.class);
    private final ProjectOauthClientToRegisteredClientMapper mapper = mock(ProjectOauthClientToRegisteredClientMapper.class);
    private final JdbcRegisteredClientRepository global = mock(JdbcRegisteredClientRepository.class);
    private final CompositeRegisteredClientRepository composite =
            new CompositeRegisteredClientRepository(projectRepo, mapper, global);

    @Test
    void findByIdResolvesProjectClientFirst() {
        UUID id = UUID.randomUUID();
        ProjectOauthClient entity = projectClient(id);
        RegisteredClient mapped = mock(RegisteredClient.class);
        when(projectRepo.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toRegisteredClient(entity)).thenReturn(mapped);

        RegisteredClient result = composite.findById(id.toString());

        assertThat(result).isSameAs(mapped);
        verify(global, never()).findById(any());
    }

    @Test
    void findByIdFallsBackToGlobalWhenNotAProjectClient() {
        UUID id = UUID.randomUUID();
        RegisteredClient bootstrap = mock(RegisteredClient.class);
        when(projectRepo.findById(id)).thenReturn(Optional.empty());
        when(global.findById(id.toString())).thenReturn(bootstrap);

        RegisteredClient result = composite.findById(id.toString());

        assertThat(result).isSameAs(bootstrap);
    }

    @Test
    void findByIdFallsBackToGlobalForNonUuidIds() {
        // El cliente bootstrap usa un id configurado (string), no un UUID.
        RegisteredClient bootstrap = mock(RegisteredClient.class);
        when(global.findById("nexus-oidc-client")).thenReturn(bootstrap);

        RegisteredClient result = composite.findById("nexus-oidc-client");

        assertThat(result).isSameAs(bootstrap);
        verify(projectRepo, never()).findById(any());
    }

    @Test
    void findByClientIdResolvesProjectClientFirst() {
        String clientId = "nxo-abc";
        ProjectOauthClient entity = projectClient(UUID.randomUUID());
        RegisteredClient mapped = mock(RegisteredClient.class);
        when(projectRepo.findByClientId(clientId)).thenReturn(Optional.of(entity));
        when(mapper.toRegisteredClient(entity)).thenReturn(mapped);

        RegisteredClient result = composite.findByClientId(clientId);

        assertThat(result).isSameAs(mapped);
        verify(global, never()).findByClientId(any());
    }

    @Test
    void findByClientIdFallsBackToGlobal() {
        RegisteredClient bootstrap = mock(RegisteredClient.class);
        when(projectRepo.findByClientId("oidc-client")).thenReturn(Optional.empty());
        when(global.findByClientId("oidc-client")).thenReturn(bootstrap);

        RegisteredClient result = composite.findByClientId("oidc-client");

        assertThat(result).isSameAs(bootstrap);
    }

    @Test
    void saveAlwaysDelegatesToGlobal() {
        RegisteredClient rc = mock(RegisteredClient.class);

        composite.save(rc);

        verify(global).save(rc);
        verify(projectRepo, never()).save(any());
    }

    private static ProjectOauthClient projectClient(UUID id) {
        return new ProjectOauthClient(
                UUID.randomUUID(), "nxo-" + id, "{bcrypt}x", "Web",
                List.of("https://app/cb"), List.of(), List.of("authorization_code", "refresh_token"),
                List.of("openid"), true, false, null);
    }
}
