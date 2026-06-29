package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchiveProjectServiceTests {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ArchiveProjectService service = new ArchiveProjectService(projectRepository);

    @Test
    void archivesProject() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("acme-app", "Acme App", null, null);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.archive(projectId);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        verify(projectRepository).save(project);
    }

    @Test
    void throwsWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(projectId))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
