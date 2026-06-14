package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectLoginControllerTests {

    private final ProjectSlugResolver projectSlugResolver = mock(ProjectSlugResolver.class);
    private final ProjectLoginController controller =
            new ProjectLoginController(projectSlugResolver);

    @Test
    void mapsOnlyMissingProjectsToNotFound() {
        when(projectSlugResolver.resolve("missing"))
                .thenThrow(new ProjectNotFoundException("missing"));

        assertThatThrownBy(() -> controller.login("missing", new ConcurrentModel()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void propagatesUnexpectedFailures() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(projectSlugResolver.resolve("f-shop")).thenThrow(failure);

        assertThatThrownBy(() -> controller.login("f-shop", new ConcurrentModel()))
                .isSameAs(failure);
    }
}
