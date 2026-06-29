package dev.unzor.nexus.modules.application.service;

import dev.unzor.nexus.modules.api.dto.ProjectModuleStatus;
import dev.unzor.nexus.modules.domain.entity.ProjectModule;
import dev.unzor.nexus.modules.domain.enums.NexusModule;
import dev.unzor.nexus.modules.domain.exception.ModuleNotFoundException;
import dev.unzor.nexus.modules.persistence.repository.ProjectModuleRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectModuleServiceTests {

    private final ProjectModuleRepository projectModuleRepository = mock(ProjectModuleRepository.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final ProjectModuleService service =
            new ProjectModuleService(projectModuleRepository, projectLookupService);

    @Test
    void listForProjectReturnsAllModulesWithDefaultFlagsForFreshProject() {
        UUID projectId = UUID.randomUUID();
        when(projectLookupService.requireById(projectId)).thenReturn(activeProject(projectId));
        when(projectModuleRepository.findAllByProjectId(projectId)).thenReturn(List.of());

        List<ProjectModuleStatus> result = service.listForProject(projectId);

        assertThat(result).hasSize(11);
        assertThat(result.stream().filter(ProjectModuleStatus::enabled).map(ProjectModuleStatus::key))
                .containsExactlyInAnyOrder("identity", "permissions", "registry", "audit");
        assertThat(result.stream().filter(m -> m.key().equals("identity")))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.enabled()).isTrue();
                    assertThat(m.enabledByDefault()).isTrue();
                });
        assertThat(result.stream().filter(m -> m.key().equals("vault")))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.enabled()).isFalse();
                    assertThat(m.enabledByDefault()).isFalse();
                });
    }

    @Test
    void listForProjectThrowsWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectLookupService.requireById(projectId))
                .thenThrow(new ProjectNotFoundException(projectId.toString()));

        assertThatThrownBy(() -> service.listForProject(projectId))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void listForProjectUsesStoredOverrideWhenPresent() {
        UUID projectId = UUID.randomUUID();
        when(projectLookupService.requireById(projectId)).thenReturn(activeProject(projectId));
        ProjectModule stored = new ProjectModule(projectId, NexusModule.VAULT, true);
        when(projectModuleRepository.findAllByProjectId(projectId)).thenReturn(List.of(stored));

        List<ProjectModuleStatus> result = service.listForProject(projectId);

        assertThat(result.stream().filter(m -> m.key().equals("vault")))
                .singleElement()
                .satisfies(m -> assertThat(m.enabled()).isTrue());
    }

    @Test
    void setEnabledPersistsAndFlipsValue() {
        UUID projectId = UUID.randomUUID();
        ProjectModule existing = new ProjectModule(projectId, NexusModule.NOTIFY, false);
        when(projectModuleRepository.findByProjectIdAndModule(projectId, NexusModule.NOTIFY))
                .thenReturn(Optional.of(existing));
        when(projectModuleRepository.save(any(ProjectModule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModuleStatus result = service.setEnabled(projectId, NexusModule.NOTIFY, true);

        assertThat(result.key()).isEqualTo("notify");
        assertThat(result.enabled()).isTrue();
        assertThat(existing.isEnabled()).isTrue();
        verify(projectModuleRepository).save(existing);
    }

    @Test
    void setEnabledUpsertsWhenNoRowExists() {
        UUID projectId = UUID.randomUUID();
        when(projectModuleRepository.findByProjectIdAndModule(projectId, NexusModule.STORAGE))
                .thenReturn(Optional.empty());
        when(projectModuleRepository.save(any(ProjectModule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModuleStatus result = service.setEnabled(projectId, NexusModule.STORAGE, true);

        assertThat(result.enabled()).isTrue();
        verify(projectModuleRepository).save(any(ProjectModule.class));
    }

    @Test
    void fromKeyThrowsForUnknownModule() {
        assertThatThrownBy(() -> NexusModule.fromKey("bogus"))
                .isInstanceOf(ModuleNotFoundException.class)
                .satisfies(ex -> assertThat(((ModuleNotFoundException) ex).getKey()).isEqualTo("bogus"));
    }

    private static Project activeProject(UUID projectId) {
        return new Project("acme-app", "Acme App", null, null);
    }
}
