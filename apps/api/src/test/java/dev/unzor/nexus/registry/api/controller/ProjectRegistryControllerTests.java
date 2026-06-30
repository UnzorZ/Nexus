package dev.unzor.nexus.registry.api.controller;

import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
import dev.unzor.nexus.registry.application.service.RegistryHeartbeatService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectRegistryControllerTests {

    private final RegistryHeartbeatService service = mock(RegistryHeartbeatService.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final ProjectRegistryController controller =
            new ProjectRegistryController(service, projectAccessService);

    @Test
    void listDelegatesAfterRequireAccess() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = authentication(principal);
        when(service.listForProject(projectId)).thenReturn(List.of());

        controller.list(projectId, principal, authentication);

        verify(projectAccessService).requireAccess(projectId, accountId, false);
        verify(service).listForProject(projectId);
    }

    @Test
    void listByNonMemberThrowsPermissionDenied() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = authentication(principal);
        doThrow(new ProjectAccessDeniedException(projectId, accountId))
                .when(projectAccessService).requireAccess(projectId, accountId, false);

        assertThatThrownBy(() -> controller.list(projectId, principal, authentication))
                .isInstanceOf(ProjectAccessDeniedException.class);
        verifyNoInteractions(service);
    }

    private static UsernamePasswordAuthenticationToken authentication(AuthenticatedAccount principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
