package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackChannelLogoutClientResolverTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProjectOauthClientRepository clientRepo = mock(ProjectOauthClientRepository.class);
    private final BackChannelLogoutClientResolver resolver =
            new BackChannelLogoutClientResolver(jdbc, clientRepo);

    @Test
    void resolvesClientWithAuthorizationAndBackchannelUriForTheProject() {
        UUID projectId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        ProjectOauthClient client = client(projectId, "nxo-x");
        client.updateBackchannelLogoutUri("https://app/backchannel");

        when(jdbc.queryForList(anyString(), eq(String.class), eq("alice")))
                .thenReturn(List.of(clientId.toString()));
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));

        List<ProjectOauthClient> result = resolver.resolve("alice", projectId);

        assertThat(result).extracting(ProjectOauthClient::getClientId).containsExactly("nxo-x");
    }

    @Test
    void skipsClientWithoutBackchannelLogoutUri() {
        UUID projectId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        ProjectOauthClient client = client(projectId, "nxo-x"); // sin backchannel_logout_uri

        when(jdbc.queryForList(anyString(), eq(String.class), eq("alice")))
                .thenReturn(List.of(clientId.toString()));
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));

        assertThat(resolver.resolve("alice", projectId)).isEmpty();
    }

    @Test
    void skipsClientOfADifferentProject() {
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        ProjectOauthClient client = client(otherProjectId, "nxo-x"); // otro proyecto
        client.updateBackchannelLogoutUri("https://app/backchannel");

        when(jdbc.queryForList(anyString(), eq(String.class), eq("alice")))
                .thenReturn(List.of(clientId.toString()));
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));

        assertThat(resolver.resolve("alice", projectId)).isEmpty();
    }

    @Test
    void ignoresNonUuidRegisteredClientIds() {
        UUID projectId = UUID.randomUUID();
        // El cliente bootstrap usa un id string no-UUID → se ignora sin explotar.
        when(jdbc.queryForList(anyString(), eq(String.class), eq("alice")))
                .thenReturn(List.of("nexus-oidc-client"));

        assertThat(resolver.resolve("alice", projectId)).isEmpty();
    }

    private static ProjectOauthClient client(UUID projectId, String clientId) {
        return new ProjectOauthClient(
                projectId, clientId, "hash", "Web",
                List.of("https://app/cb"), List.of(), List.of("authorization_code"), List.of("openid"),
                true, false, null);
    }
}
