package dev.unzor.nexus.projects.api.controller;

import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import dev.unzor.nexus.projects.api.dto.ProjectDetails;
import dev.unzor.nexus.projects.api.dto.ProjectSummary;
import dev.unzor.nexus.projects.api.requests.CreateProjectRequest;
import dev.unzor.nexus.projects.application.service.CreateProjectService;
import dev.unzor.nexus.projects.application.service.GetProjectService;
import dev.unzor.nexus.projects.application.service.ListAccessibleProjectsService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/panel/v1/projects")
class ProjectsController {

    private final CreateProjectService createProjectService;
    private final GetProjectService getProjectService;
    private final ListAccessibleProjectsService listAccessibleProjectsService;
    private final ProjectAccessService projectAccessService;

    ProjectsController(
            CreateProjectService createProjectService,
            GetProjectService getProjectService,
            ListAccessibleProjectsService listAccessibleProjectsService,
            ProjectAccessService projectAccessService
    ) {
        this.createProjectService = createProjectService;
        this.getProjectService = getProjectService;
        this.listAccessibleProjectsService = listAccessibleProjectsService;
        this.projectAccessService = projectAccessService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProjectDetails createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal
    ) {
        return createProjectService.createProject(
                request.slug(),
                request.name(),
                request.description(),
                request.publicBaseUrl(),
                principal.accountId()
        );
    }

    @GetMapping
    List<ProjectSummary> listAccessibleProjects(
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
        return listAccessibleProjectsService.listAccessible(
                principal.accountId(),
                isInstanceAdmin
        );
    }

    @GetMapping("/{projectId}")
    ProjectDetails getProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return getProjectService.getById(projectId);
    }
}
