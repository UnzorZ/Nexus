package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.projects.api.dto.ProjectSlugReference;
import dev.unzor.nexus.projects.application.service.ResolveProjectBySlugService;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectSlugResolverTests {

    private final ResolveProjectBySlugService resolveProjectBySlugService =
            mock(ResolveProjectBySlugService.class);
    private final ProjectSlugResolver resolver =
            new ProjectSlugResolver(resolveProjectBySlugService);

    @Test
    void resolvesProjectSlugToContext() {
        UUID projectId = UUID.randomUUID();
        when(resolveProjectBySlugService.resolve("acme-app"))
                .thenReturn(new ProjectSlugReference(projectId, "acme-app"));

        ProjectAuthenticationContext context = resolver.resolve("acme-app");

        assertThat(context.projectId()).isEqualTo(projectId);
        assertThat(context.projectSlug()).isEqualTo("acme-app");
    }

    @Test
    void rejectsUnknownProjectSlug() {
        when(resolveProjectBySlugService.resolve("missing"))
                .thenThrow(new ProjectNotFoundException("missing"));

        assertThatThrownBy(() -> resolver.resolve("missing"))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
