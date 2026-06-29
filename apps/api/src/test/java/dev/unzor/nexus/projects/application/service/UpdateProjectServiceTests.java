package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectDetails;
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

class UpdateProjectServiceTests {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final UpdateProjectService service =
            new UpdateProjectService(projectRepository, projectAccessService);

    @Test
    void updatesEditableFieldsAndKeepsSlug() {
        UUID projectId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Project project = new Project("acme-app", "Acme App", "Old description", "https://old.example.com");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectAccessService.canManage(projectId, accountId, false)).thenReturn(true);
        when(projectAccessService.canDelete(projectId, accountId, false)).thenReturn(true);

        ProjectDetails result = service.update(
                projectId,
                "Acme App Pro",
                "New description",
                "https://new.example.com",
                accountId,
                false
        );

        assertThat(result.slug()).isEqualTo("acme-app");
        assertThat(result.name()).isEqualTo("Acme App Pro");
        assertThat(result.description()).isEqualTo("New description");
        assertThat(result.publicBaseUrl()).isEqualTo("https://new.example.com");
        assertThat(result.canManage()).isTrue();
        assertThat(result.canDelete()).isTrue();
        verify(projectRepository).saveAndFlush(project);
    }

    @Test
    void throwsWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                projectId,
                "Name",
                null,
                null,
                UUID.randomUUID(),
                false
        )).isInstanceOf(ProjectNotFoundException.class);
    }
}
