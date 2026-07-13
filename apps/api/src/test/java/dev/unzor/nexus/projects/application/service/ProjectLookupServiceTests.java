package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectLookupServiceTests {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ProjectLookupService service = new ProjectLookupService(projectRepository, entityManager);

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

    @Test
    void requireOperationalByIdAcceptsActiveProject() {
        UUID projectId = UUID.randomUUID();
        Project project = project(ProjectStatus.ACTIVE);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatCode(() -> service.requireOperationalById(projectId))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"SUSPENDED", "ARCHIVED"})
    void requireOperationalByIdRejectsInactiveProject(ProjectStatus status) {
        UUID projectId = UUID.randomUUID();
        Project project = project(status);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.requireOperationalById(projectId))
                .isInstanceOfSatisfying(ProjectNotOperationalException.class, exception -> {
                    assertThat(exception.getProjectId()).isEqualTo(projectId);
                    assertThat(exception.getStatus()).isEqualTo(status);
                });
    }

    @Test
    void requireOperationalByIdThrowsNotFoundWhenMissing() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOperationalById(projectId))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"SUSPENDED", "ARCHIVED"})
    void managementLookupsContinueToAcceptInactiveProjects(ProjectStatus status) {
        UUID projectId = UUID.randomUUID();
        Project project = project(status);
        when(project.getSlug()).thenReturn("acme-app");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThat(service.requireById(projectId)).isSameAs(project);
        assertThat(service.requireSlug(projectId)).isEqualTo("acme-app");
    }

    @Test
    void lockOperationalByIdUsesTheSharedLockLookup() {
        UUID projectId = UUID.randomUUID();
        Project project = project(ProjectStatus.ACTIVE);
        when(projectRepository.findForShareById(projectId)).thenReturn(Optional.of(project));

        assertThatCode(() -> service.lockOperationalById(projectId)).doesNotThrowAnyException();

        verify(projectRepository).findForShareById(projectId);
        verify(entityManager).refresh(project, LockModeType.PESSIMISTIC_READ);
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"SUSPENDED", "ARCHIVED"})
    void lockOperationalByIdRejectsInactiveProject(ProjectStatus status) {
        UUID projectId = UUID.randomUUID();
        Project project = project(status);
        when(projectRepository.findForShareById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.lockOperationalById(projectId))
                .isInstanceOf(ProjectNotOperationalException.class);
    }

    private static Project project(ProjectStatus status) {
        Project project = mock(Project.class);
        when(project.getStatus()).thenReturn(status);
        when(project.isOperational()).thenReturn(status == ProjectStatus.ACTIVE);
        return project;
    }
}
