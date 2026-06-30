package dev.unzor.nexus.projects.api.controller;

import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import dev.unzor.nexus.projects.api.dto.ProjectDetails;
import dev.unzor.nexus.projects.api.dto.ProjectSummary;
import dev.unzor.nexus.projects.api.requests.CreateProjectRequest;
import dev.unzor.nexus.projects.api.requests.UpdateProjectRequest;
import dev.unzor.nexus.projects.application.service.ArchiveProjectService;
import dev.unzor.nexus.projects.application.service.CreateProjectService;
import dev.unzor.nexus.projects.application.service.GetProjectService;
import dev.unzor.nexus.projects.application.service.ListAccessibleProjectsService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.projects.application.service.RestoreProjectService;
import dev.unzor.nexus.projects.application.service.UpdateProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final UpdateProjectService updateProjectService;
    private final ArchiveProjectService archiveProjectService;
    private final RestoreProjectService restoreProjectService;

    ProjectsController(
            CreateProjectService createProjectService,
            GetProjectService getProjectService,
            ListAccessibleProjectsService listAccessibleProjectsService,
            ProjectAccessService projectAccessService,
            UpdateProjectService updateProjectService,
            ArchiveProjectService archiveProjectService,
            RestoreProjectService restoreProjectService
    ) {
        this.createProjectService = createProjectService;
        this.getProjectService = getProjectService;
        this.listAccessibleProjectsService = listAccessibleProjectsService;
        this.projectAccessService = projectAccessService;
        this.updateProjectService = updateProjectService;
        this.archiveProjectService = archiveProjectService;
        this.restoreProjectService = restoreProjectService;
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
        return listAccessibleProjectsService.listAccessible(principal.accountId());
    }

    @GetMapping("/{projectId}")
    ProjectDetails getProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return getProjectService.getById(projectId, principal.accountId(), isInstanceAdmin);
    }

    @PatchMapping("/{projectId}")
    ProjectDetails updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        // requireManage already enforces an ACTIVE membership (it looks up the
        // membership and checks role), so a separate requireAccess query here is
        // redundant. It throws the same ProjectAccessDeniedException either way.
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return updateProjectService.update(
                projectId,
                request.name(),
                request.description(),
                request.publicBaseUrl(),
                principal.accountId(),
                isInstanceAdmin
        );
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archiveProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        // requireDelete already enforces an ACTIVE OWNER membership; no need for
        // a separate requireAccess query (see updateProject).
        projectAccessService.requireDelete(projectId, principal.accountId(), isInstanceAdmin);
        archiveProjectService.archive(projectId, principal.accountId());
    }

    @PostMapping("/{projectId}/restore")
    ProjectDetails restoreProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        // Restore is the inverse of archive, so it requires the same OWNER-level
        // privilege (see archiveProject). requireDelete already enforces an ACTIVE
        // OWNER membership, so no separate requireAccess query is needed.
        projectAccessService.requireDelete(projectId, principal.accountId(), isInstanceAdmin);
        restoreProjectService.restore(projectId, principal.accountId());
        return getProjectService.getById(projectId, principal.accountId(), isInstanceAdmin);
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
