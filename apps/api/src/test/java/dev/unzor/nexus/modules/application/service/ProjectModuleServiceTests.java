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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectModuleServiceTests {

    private final ProjectModuleRepository projectModuleRepository = mock(ProjectModuleRepository.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final ProjectModuleService service =
            new ProjectModuleService(projectModuleRepository, projectLookupService, noopTransactionManager(),
                    mock(ApplicationEventPublisher.class));

    @Test
    void listForProjectReturnsAllModulesWithDefaultFlagsForFreshProject() {
        UUID projectId = UUID.randomUUID();
        when(projectLookupService.requireById(projectId)).thenReturn(activeProject(projectId));
        when(projectModuleRepository.findAllByProjectId(projectId)).thenReturn(List.of());

        List<ProjectModuleStatus> result = service.listForProject(projectId);

        assertThat(result).hasSize(11);
        // Los habilitados por defecto deben reflejar exactamente el catálogo
        // (NexusModule.enabledByDefault); se deriva del enum para no acoplar el
        // test a la lista literal y que crezca con cada módulo nuevo.
        assertThat(result.stream().filter(ProjectModuleStatus::enabled).map(ProjectModuleStatus::key))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(NexusModule.values())
                                .filter(NexusModule::enabledByDefault)
                                .map(NexusModule::key)
                                .toList());
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
        when(projectLookupService.requireById(projectId)).thenReturn(activeProject(projectId));
        when(projectModuleRepository.findByProjectIdAndModule(projectId, NexusModule.NOTIFY))
                .thenReturn(Optional.of(existing));
        when(projectModuleRepository.save(any(ProjectModule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModuleStatus result = service.setEnabled(projectId, NexusModule.NOTIFY, true, UUID.randomUUID());

        assertThat(result.key()).isEqualTo("notify");
        assertThat(result.enabled()).isTrue();
        assertThat(existing.isEnabled()).isTrue();
        verify(projectModuleRepository).save(existing);
    }

    @Test
    void setEnabledUpsertsWhenNoRowExists() {
        UUID projectId = UUID.randomUUID();
        when(projectLookupService.requireById(projectId)).thenReturn(activeProject(projectId));
        when(projectModuleRepository.findByProjectIdAndModule(projectId, NexusModule.STORAGE))
                .thenReturn(Optional.empty());
        when(projectModuleRepository.save(any(ProjectModule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModuleStatus result = service.setEnabled(projectId, NexusModule.STORAGE, true, UUID.randomUUID());

        assertThat(result.enabled()).isTrue();
        verify(projectModuleRepository).save(any(ProjectModule.class));
    }

    @Test
    void setEnabledThrowsWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectLookupService.requireById(projectId))
                .thenThrow(new ProjectNotFoundException(projectId.toString()));

        assertThatThrownBy(() -> service.setEnabled(projectId, NexusModule.VAULT, true, UUID.randomUUID()))
                .isInstanceOf(ProjectNotFoundException.class);
        verify(projectModuleRepository, never()).save(any(ProjectModule.class));
    }

    @Test
    void setEnabledRetriesWhenAConcurrentInsertWinsTheRace() {
        UUID projectId = UUID.randomUUID();
        ProjectModule winnerRow = new ProjectModule(projectId, NexusModule.VAULT, false);
        when(projectLookupService.requireById(projectId)).thenReturn(activeProject(projectId));
        // Attempt 1 loses the race: the row is not visible yet, and the insert
        // violates the unique constraint because a concurrent request inserted
        // first. Attempt 2 sees the winner's row and updates it.
        when(projectModuleRepository.findByProjectIdAndModule(projectId, NexusModule.VAULT))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerRow));
        when(projectModuleRepository.save(any(ProjectModule.class)))
                .thenThrow(new DataIntegrityViolationException("uk_project_module_project_module"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModuleStatus result = service.setEnabled(projectId, NexusModule.VAULT, true, UUID.randomUUID());

        assertThat(result.enabled()).isTrue();
        assertThat(winnerRow.isEnabled()).isTrue();
        verify(projectModuleRepository, times(2)).save(any(ProjectModule.class));
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

    /**
     * Gestor de transacciones sin efecto real: ejecuta el callback de forma
     * síncrona para que el {@code TransactionTemplate} del servicio pueda probarse
     * con mocks sin levantar un contexto Spring ni una base de datos. Permite que
     * las excepciones del callback (p. ej. la violación de restricción simulada)
     * se propaguen tal como lo harían en una transacción real.
     */
    private static PlatformTransactionManager noopTransactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected boolean isExistingTransaction(Object transaction) {
                return false;
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                // no-op
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                // no-op
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                // no-op
            }
        };
    }
}
