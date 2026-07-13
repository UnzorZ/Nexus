package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.application.ProjectArchived;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchiveProjectServiceTests {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ArchiveProjectService service =
            new ArchiveProjectService(projectRepository, eventPublisher);

    @Test
    void archivesProject() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("acme-app", "Acme App", null, null);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.archive(projectId, UUID.randomUUID());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        var order = inOrder(projectRepository, eventPublisher);
        order.verify(projectRepository).findById(projectId);
        order.verify(projectRepository).saveAndFlush(project);
        order.verify(eventPublisher).publishEvent(new ProjectArchived(projectId));
        order.verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.isA(AuditEvent.class));
    }

    @Test
    void throwsWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(projectId, UUID.randomUUID()))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void publishesTransitionAndAuditOnlyOnceWhenArchivedTwice() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("acme-app", "Acme App", null, null);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        service.archive(projectId, UUID.randomUUID());
        service.archive(projectId, UUID.randomUUID());

        verify(projectRepository, times(2)).findById(projectId);
        verify(projectRepository).saveAndFlush(project);
        verify(eventPublisher).publishEvent(new ProjectArchived(projectId));
        verify(eventPublisher, times(2)).publishEvent(org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void doesNotPersistOrPublishWhenAlreadyArchived() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("acme-app", "Acme App", null, null);
        project.archive();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        service.archive(projectId, UUID.randomUUID());

        verify(projectRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.<Object>any());
    }
}
