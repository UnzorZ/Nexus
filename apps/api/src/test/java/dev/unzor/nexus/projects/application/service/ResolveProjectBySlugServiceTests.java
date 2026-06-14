package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectSlugReference;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolveProjectBySlugServiceTests {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ResolveProjectBySlugService service =
            new ResolveProjectBySlugService(projectRepository);

    @Test
    void returnsTheCanonicalStoredSlug() {
        UUID projectId = UUID.randomUUID();
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(projectId);
        when(project.getSlug()).thenReturn("f-shop");
        when(projectRepository.findBySlugIgnoreCase("F-SHOP"))
                .thenReturn(Optional.of(project));

        ProjectSlugReference reference = service.resolve("F-SHOP");

        assertThat(reference.projectId()).isEqualTo(projectId);
        assertThat(reference.projectSlug()).isEqualTo("f-shop");
    }
}
