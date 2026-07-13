package dev.unzor.nexus.identity.persistence;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectOperationalOAuth2AuthorizationServiceTests {

    private final OAuth2AuthorizationService delegate = mock(OAuth2AuthorizationService.class);
    private final ProjectOauthClientRepository clientRepository = mock(ProjectOauthClientRepository.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final ProjectOperationalOAuth2AuthorizationService service =
            new ProjectOperationalOAuth2AuthorizationService(
                    delegate, clientRepository, projectLookupService);

    @Test
    void projectAuthorizationLocksOperationalProjectBeforeSaving() {
        UUID clientId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        OAuth2Authorization authorization = authorization(clientId.toString());
        ProjectOauthClient client = mock(ProjectOauthClient.class);
        when(client.getProjectId()).thenReturn(projectId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        service.save(authorization);

        var order = inOrder(clientRepository, projectLookupService, delegate);
        order.verify(clientRepository).findById(clientId);
        order.verify(projectLookupService).lockOperationalById(projectId);
        order.verify(delegate).save(authorization);
    }

    @Test
    void inactiveProjectIsTranslatedToInvalidGrantAndNotSaved() {
        UUID clientId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        OAuth2Authorization authorization = authorization(clientId.toString());
        ProjectOauthClient client = mock(ProjectOauthClient.class);
        when(client.getProjectId()).thenReturn(projectId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        org.mockito.Mockito.doThrow(
                        new ProjectNotOperationalException(projectId, ProjectStatus.ARCHIVED))
                .when(projectLookupService).lockOperationalById(projectId);

        assertThatThrownBy(() -> service.save(authorization))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        error -> assertThat(error.getError().getErrorCode())
                                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
        verify(delegate, never()).save(authorization);
    }

    @Test
    void globalAuthorizationDelegatesWithoutProjectLock() {
        OAuth2Authorization authorization = authorization("technical-global-client");

        service.save(authorization);

        verify(delegate).save(authorization);
        verify(clientRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(projectLookupService, never()).lockOperationalById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void uuidGlobalAuthorizationDelegatesWhenNoProjectClientExists() {
        UUID registeredClientId = UUID.randomUUID();
        OAuth2Authorization authorization = authorization(registeredClientId.toString());
        when(clientRepository.findById(registeredClientId)).thenReturn(Optional.empty());

        service.save(authorization);

        verify(delegate).save(authorization);
        verify(projectLookupService, never()).lockOperationalById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removeAndReadsDelegateUnchanged() {
        OAuth2Authorization authorization = authorization("global");
        OAuth2Authorization byId = mock(OAuth2Authorization.class);
        OAuth2Authorization byToken = mock(OAuth2Authorization.class);
        OAuth2TokenType tokenType = OAuth2TokenType.ACCESS_TOKEN;
        when(delegate.findById("authorization-id")).thenReturn(byId);
        when(delegate.findByToken("token", tokenType)).thenReturn(byToken);

        service.remove(authorization);

        assertThat(service.findById("authorization-id")).isSameAs(byId);
        assertThat(service.findByToken("token", tokenType)).isSameAs(byToken);
        verify(delegate).remove(authorization);
    }

    @Test
    void hydratedAuthorizationForInactiveProjectIsReturnedAsMissingNotAsHydrationFailure() {
        UUID clientId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        OAuth2Authorization authorization = authorization(clientId.toString());
        ProjectOauthClient client = mock(ProjectOauthClient.class);
        when(client.getProjectId()).thenReturn(projectId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(delegate.findByToken("refresh-token", OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);
        org.mockito.Mockito.doThrow(
                        new ProjectNotOperationalException(projectId, ProjectStatus.ARCHIVED))
                .when(projectLookupService).requireOperationalById(projectId);

        assertThat(service.findByToken("refresh-token", OAuth2TokenType.REFRESH_TOKEN))
                .isNull();
    }

    private static OAuth2Authorization authorization(String registeredClientId) {
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorization.getRegisteredClientId()).thenReturn(registeredClientId);
        return authorization;
    }
}
