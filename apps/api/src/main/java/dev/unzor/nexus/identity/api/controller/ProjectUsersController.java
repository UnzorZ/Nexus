package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.api.dto.ProjectUserDetails;
import dev.unzor.nexus.identity.api.requests.CreateProjectUserRequest;
import dev.unzor.nexus.identity.api.requests.ResetPasswordRequest;
import dev.unzor.nexus.identity.api.requests.UpdateProjectUserRequest;
import dev.unzor.nexus.identity.application.service.CreateProjectUserService;
import dev.unzor.nexus.identity.application.service.DeleteProjectUserService;
import dev.unzor.nexus.identity.application.service.ProjectUserQueryService;
import dev.unzor.nexus.identity.application.service.ProjectUserStatusService;
import dev.unzor.nexus.identity.application.service.ResetProjectUserPasswordService;
import dev.unzor.nexus.identity.application.service.UpdateProjectUserService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
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

/**
 * CRUD panel de usuarios finales de un proyecto (su realm OAuth/OIDC). El acceso
 * de lectura es {@code requireAccess}; las mutaciones, {@code requireManage}.
 * El instance admin siempre pasa. Espejea {@code ProjectMembersController}.
 */
@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/users")
class ProjectUsersController {

    private final ProjectUserQueryService queryService;
    private final CreateProjectUserService createService;
    private final UpdateProjectUserService updateService;
    private final ProjectUserStatusService statusService;
    private final DeleteProjectUserService deleteService;
    private final ResetProjectUserPasswordService resetPasswordService;
    private final ProjectAccessService projectAccessService;

    ProjectUsersController(
            ProjectUserQueryService queryService,
            CreateProjectUserService createService,
            UpdateProjectUserService updateService,
            ProjectUserStatusService statusService,
            DeleteProjectUserService deleteService,
            ResetProjectUserPasswordService resetPasswordService,
            ProjectAccessService projectAccessService
    ) {
        this.queryService = queryService;
        this.createService = createService;
        this.updateService = updateService;
        this.statusService = statusService;
        this.deleteService = deleteService;
        this.resetPasswordService = resetPasswordService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<ProjectUserDetails> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return queryService.list(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProjectUserDetails create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectUserRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return createService.create(
                projectId, request.email(), request.username(), request.displayName(),
                request.password(), principal.accountId());
    }

    @GetMapping("/{userId}")
    ProjectUserDetails get(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return queryService.get(projectId, userId);
    }

    @PatchMapping("/{userId}")
    ProjectUserDetails update(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateProjectUserRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return updateService.update(
                projectId, userId, request.displayName(), request.username(), principal.accountId());
    }

    @PostMapping("/{userId}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void suspend(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        statusService.suspend(projectId, userId, principal.accountId());
    }

    @PostMapping("/{userId}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        statusService.reactivate(projectId, userId, principal.accountId());
    }

    @PostMapping("/{userId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disable(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        statusService.disable(projectId, userId, principal.accountId());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        deleteService.delete(projectId, userId, principal.accountId());
    }

    @PostMapping("/{userId}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        resetPasswordService.reset(projectId, userId, request.newPassword(), principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
