package dev.unzor.nexus.projects.domain.entity;

import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTests {

    @Test
    void newProjectsStartActive() {
        Project project = new Project(
                "acme-app",
                "Acme App",
                "Sample description",
                "https://app.example.com"
        );

        assertThat(project.getSlug()).isEqualTo("acme-app");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(project.isOperational()).isTrue();
    }

    @Test
    void lifecycleTransitionsControlWhetherTheProjectIsOperational() {
        Project project = new Project("acme-app", "Acme App", null, null);

        project.suspend();
        assertThat(project.isOperational()).isFalse();

        project.reactivate();
        assertThat(project.isOperational()).isTrue();

        project.archive();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(project.isOperational()).isFalse();
    }
}
