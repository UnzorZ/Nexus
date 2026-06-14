package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.projects.application.service.ResolveProjectBySlugService;
import dev.unzor.nexus.projects.api.dto.ProjectSlugReference;
import org.springframework.stereotype.Service;

@Service
public class ProjectSlugResolver {

    private final ResolveProjectBySlugService resolveProjectBySlugService;

    public ProjectSlugResolver(ResolveProjectBySlugService resolveProjectBySlugService) {
        this.resolveProjectBySlugService = resolveProjectBySlugService;
    }

    public ProjectAuthenticationContext resolve(String projectSlug) {
        ProjectSlugReference reference = resolveProjectBySlugService.resolve(projectSlug);
        return new ProjectAuthenticationContext(reference.projectId(), reference.projectSlug());
    }
}
