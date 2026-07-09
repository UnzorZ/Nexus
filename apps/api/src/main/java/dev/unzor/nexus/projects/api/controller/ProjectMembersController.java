package dev.unzor.nexus.projects.api.controller;

import dev.unzor.nexus.projects.api.dto.MembershipDetails;
import dev.unzor.nexus.projects.api.requests.InviteMemberRequest;
import dev.unzor.nexus.projects.api.requests.UpdateMemberRoleRequest;
import dev.unzor.nexus.projects.application.service.ChangeMemberRoleService;
import dev.unzor.nexus.projects.application.service.InviteMemberService;
import dev.unzor.nexus.projects.application.service.ListProjectMembersService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.projects.application.service.RemoveMemberService;
import dev.unzor.nexus.projects.application.service.TransferOwnershipService;
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

@RestController
@RequestMapping("/api/panel/v1/projects/{projectId}/members")
class ProjectMembersController {

    private final ListProjectMembersService listProjectMembersService;
    private final InviteMemberService inviteMemberService;
    private final ChangeMemberRoleService changeMemberRoleService;
    private final RemoveMemberService removeMemberService;
    private final TransferOwnershipService transferOwnershipService;
    private final ProjectAccessService projectAccessService;

    ProjectMembersController(
            ListProjectMembersService listProjectMembersService,
            InviteMemberService inviteMemberService,
            ChangeMemberRoleService changeMemberRoleService,
            RemoveMemberService removeMemberService,
            TransferOwnershipService transferOwnershipService,
            ProjectAccessService projectAccessService
    ) {
        this.listProjectMembersService = listProjectMembersService;
        this.inviteMemberService = inviteMemberService;
        this.changeMemberRoleService = changeMemberRoleService;
        this.removeMemberService = removeMemberService;
        this.transferOwnershipService = transferOwnershipService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    List<MembershipDetails> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireAccess(projectId, principal.accountId(), isInstanceAdmin);
        return listProjectMembersService.list(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    void invite(
            @PathVariable UUID projectId,
            @Valid @RequestBody InviteMemberRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        // requireManage ya exige una membresía activa OWNER/ADMIN; no hace falta una
        // consulta requireAccess aparte (mismo patrón que updateProject).
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        // Anti-enumeración: el servicio es no-op silencioso si el email no tiene cuenta,
        // de modo que la respuesta (200 OK, sin body) es idéntica exista o no — el admin
        // no puede inferir la existencia de la cuenta por el resultado del invite.
        inviteMemberService.invite(projectId, request.email(), request.role(), principal.accountId());
    }

    @PatchMapping("/{memberId}")
    MembershipDetails changeRole(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        return changeMemberRoleService.changeRole(projectId, memberId, request.role(), principal.accountId());
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        projectAccessService.requireManage(projectId, principal.accountId(), isInstanceAdmin);
        removeMemberService.remove(projectId, memberId, principal.accountId());
    }

    @PostMapping("/{memberId}/transfer-ownership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void transferOwnership(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal AuthenticatedAccount principal,
            Authentication authentication
    ) {
        boolean isInstanceAdmin = isInstanceAdmin(authentication);
        // Transferir la propiedad es una acción de nivel OWNER: requireDelete.
        projectAccessService.requireDelete(projectId, principal.accountId(), isInstanceAdmin);
        transferOwnershipService.transfer(projectId, memberId, principal.accountId());
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INSTANCE_ADMIN"));
    }
}
