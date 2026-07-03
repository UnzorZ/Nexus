package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.infrastructure.security.ProjectSessionAuthenticator;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectLoginControllerTests {

    private final ProjectSlugResolver projectSlugResolver = mock(ProjectSlugResolver.class);
    private final ProjectSessionAuthenticator sessionAuthenticator = mock(ProjectSessionAuthenticator.class);
    private final ProjectLoginController controller =
            new ProjectLoginController(projectSlugResolver, sessionAuthenticator);
    private final CsrfToken csrfToken = mock(CsrfToken.class);

    @Test
    void loginFormMapsMissingProjectToNotFound() {
        when(projectSlugResolver.resolve("missing"))
                .thenThrow(new ProjectNotFoundException("missing"));

        assertThatThrownBy(() -> controller.loginForm("missing", new ConcurrentModel(), csrfToken))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void meMapsMissingProjectToNotFound() {
        when(projectSlugResolver.resolve("missing"))
                .thenThrow(new ProjectNotFoundException("missing"));

        assertThatThrownBy(() -> controller.me("missing", new ConcurrentModel()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void propagatesUnexpectedFailures() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(projectSlugResolver.resolve("acme-app")).thenThrow(failure);

        assertThatThrownBy(() -> controller.loginForm("acme-app", new ConcurrentModel(), csrfToken))
                .isSameAs(failure);
    }
}
