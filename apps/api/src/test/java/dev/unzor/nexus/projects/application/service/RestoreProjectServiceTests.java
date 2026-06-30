package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestoreProjectServiceTests {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final RestoreProjectService service =
            new RestoreProjectService(projectRepository, mock(ApplicationEventPublisher.class));

    @Test
    void restoresArchivedProject() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("acme-app", "Acme App", null, null);
        project.archive();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.restore(projectId, UUID.randomUUID());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        verify(projectRepository).save(project);
    }

    @Test
    void isIdempotentForActiveProject() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("acme-app", "Acme App", null, null);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        service.restore(projectId, UUID.randomUUID());

        // An active project needs no write — and a suspended project must not be
        // reactivated by "restore" (that's a different operation).
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void doesNotReactivateSuspendedProject() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("acme-app", "Acme App", null, null);
        project.suspend();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        service.restore(projectId, UUID.randomUUID());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.SUSPENDED);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void throwsWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(projectId, UUID.randomUUID()))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
