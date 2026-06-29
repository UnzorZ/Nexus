package dev.unzor.nexus.modules.api.controller;

import dev.unzor.nexus.modules.application.service.ProjectModuleService;
import dev.unzor.nexus.modules.domain.enums.NexusModule;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.projects.domain.exception.ProjectAccessDeniedException;
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

class ProjectModulesControllerTests {

    private final ProjectModuleService projectModuleService = mock(ProjectModuleService.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final ProjectModulesController controller =
            new ProjectModulesController(projectModuleService, projectAccessService);

    @Test
    void patchByMemberThrowsPermissionDenied() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        doThrow(new ProjectAccessDeniedException(projectId, accountId))
                .when(projectAccessService)
                .requireManage(projectId, accountId, false);

        assertThatThrownBy(() -> controller.setEnabled(
                projectId,
                "identity",
                new dev.unzor.nexus.modules.api.requests.UpdateModuleStateRequest(true),
                principal,
                authentication
        )).isInstanceOf(ProjectAccessDeniedException.class);

        verifyNoInteractions(projectModuleService);
    }

    @Test
    void patchByOwnerDelegatesToService() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedAccount principal = () -> accountId;
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(projectModuleService.setEnabled(projectId, NexusModule.IDENTITY, true))
                .thenReturn(new dev.unzor.nexus.modules.api.dto.ProjectModuleStatus("identity", true, true));

        controller.setEnabled(
                projectId,
                "identity",
                new dev.unzor.nexus.modules.api.requests.UpdateModuleStateRequest(true),
                principal,
                authentication
        );

        verify(projectAccessService).requireManage(projectId, accountId, false);
        verify(projectModuleService).setEnabled(projectId, NexusModule.IDENTITY, true);
    }
}
