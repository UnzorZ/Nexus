package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectSlugReference;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        when(project.getSlug()).thenReturn("acme-app");
        when(projectRepository.findBySlugIgnoreCase("ACME-APP"))
                .thenReturn(Optional.of(project));

        ProjectSlugReference reference = service.resolve("ACME-APP");

        assertThat(reference.projectId()).isEqualTo(projectId);
        assertThat(reference.projectSlug()).isEqualTo("acme-app");
    }

    @Test
    void resolveOperationalReturnsActiveProjectWithOneLookup() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId, ProjectStatus.ACTIVE);
        when(projectRepository.findBySlugIgnoreCase("ACME-APP"))
                .thenReturn(Optional.of(project));

        ProjectSlugReference reference = service.resolveOperational("ACME-APP");

        assertThat(reference.projectId()).isEqualTo(projectId);
        assertThat(reference.projectSlug()).isEqualTo("acme-app");
        verify(projectRepository).findBySlugIgnoreCase("ACME-APP");
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"SUSPENDED", "ARCHIVED"})
    void resolveOperationalRejectsInactiveProject(ProjectStatus status) {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId, status);
        when(projectRepository.findBySlugIgnoreCase("acme-app"))
                .thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.resolveOperational("acme-app"))
                .isInstanceOfSatisfying(ProjectNotOperationalException.class, exception -> {
                    assertThat(exception.getProjectId()).isEqualTo(projectId);
                    assertThat(exception.getStatus()).isEqualTo(status);
                });
        verify(projectRepository).findBySlugIgnoreCase("acme-app");
    }

    @Test
    void resolveOperationalThrowsNotFoundWhenMissing() {
        when(projectRepository.findBySlugIgnoreCase("missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveOperational("missing"))
                .isInstanceOf(ProjectNotFoundException.class);
        verify(projectRepository).findBySlugIgnoreCase("missing");
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"SUSPENDED", "ARCHIVED"})
    void managementResolveContinuesToAcceptInactiveProjects(ProjectStatus status) {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId, status);
        when(projectRepository.findBySlugIgnoreCase("acme-app"))
                .thenReturn(Optional.of(project));

        ProjectSlugReference reference = service.resolve("acme-app");

        assertThat(reference.projectId()).isEqualTo(projectId);
        assertThat(reference.projectSlug()).isEqualTo("acme-app");
    }

    private static Project project(UUID projectId, ProjectStatus status) {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(projectId);
        when(project.getSlug()).thenReturn("acme-app");
        when(project.getStatus()).thenReturn(status);
        when(project.isOperational()).thenReturn(status == ProjectStatus.ACTIVE);
        return project;
    }
}
