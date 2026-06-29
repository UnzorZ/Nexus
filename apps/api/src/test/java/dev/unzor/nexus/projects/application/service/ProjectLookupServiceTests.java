package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectLookupServiceTests {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectLookupService service = new ProjectLookupService(projectRepository);

    @Test
    void requireByIdReturnsProjectWhenPresent() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("acme-app", "Acme App", null, null);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThat(service.requireById(projectId)).isSameAs(project);
    }

    @Test
    void requireByIdThrowsWhenMissing() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireById(projectId))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
